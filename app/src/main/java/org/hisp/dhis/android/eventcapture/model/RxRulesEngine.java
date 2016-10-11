package org.hisp.dhis.android.eventcapture.model;

import org.hisp.dhis.client.sdk.core.event.EventInteractor;
import org.hisp.dhis.client.sdk.core.program.ProgramInteractor;
import org.hisp.dhis.client.sdk.core.user.UserInteractor;
import org.hisp.dhis.client.sdk.models.event.Event;
import org.hisp.dhis.client.sdk.models.organisationunit.OrganisationUnit;
import org.hisp.dhis.client.sdk.models.program.Program;
import org.hisp.dhis.client.sdk.models.program.ProgramRule;
import org.hisp.dhis.client.sdk.models.program.ProgramRuleAction;
import org.hisp.dhis.client.sdk.models.program.ProgramRuleActionType;
import org.hisp.dhis.client.sdk.models.program.ProgramRuleVariable;
import org.hisp.dhis.client.sdk.models.trackedentity.TrackedEntityDataValue;
import org.hisp.dhis.client.sdk.models.user.User;
import org.hisp.dhis.client.sdk.rules.RuleEffect;
import org.hisp.dhis.client.sdk.rules.RuleEngine;
import org.hisp.dhis.client.sdk.utils.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rx.Observable;
import rx.Single;
import rx.SingleSubscriber;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;
import rx.subscriptions.CompositeSubscription;

import static org.hisp.dhis.client.sdk.core.ModelUtils.toMap;
import static org.hisp.dhis.client.sdk.utils.Preconditions.isNull;

public class RxRulesEngine {
    private static final String TAG = RxRulesEngine.class.getSimpleName();

    private final UserInteractor currentUserInteractor;
    private final EventInteractor eventInteractor;
    private final ProgramInteractor programInteractor;

    private Event currentEvent;
    private final Map<String, Event> eventsMap;

    // engine
    private RuleEngine ruleEngine;
    private Subject<List<RuleEffect>, List<RuleEffect>> ruleEffectSubject;

    // utilities
    private final Logger logger;
    private CompositeSubscription subscription;

    public RxRulesEngine(UserInteractor currentUserInteractor,
                         ProgramInteractor programInteractor,
                         EventInteractor eventInteractor, Logger logger) {
        this.currentUserInteractor = currentUserInteractor;
        this.programInteractor = programInteractor;
        this.eventInteractor = eventInteractor;
        this.eventsMap = new HashMap<>();
        this.logger = logger;
        this.subscription = new CompositeSubscription();
    }

    public Observable<Boolean> init(final String eventUid) {
        return Observable.create(new Observable.OnSubscribe<Event>() {
            @Override
            public void call(Subscriber<? super Event> subscriber) {
                try {
                    subscriber.onNext(eventInteractor.store().queryByUid(eventUid));
                } catch (Exception e) {
                    subscriber.onError(e);
                }
                subscriber.onCompleted();
            }
        }).switchMap(new Func1<Event, Observable<? extends Boolean>>() {
            @Override
            public Observable<? extends Boolean> call(final Event event) {
                final OrganisationUnit organisationUnit = new OrganisationUnit();
                final Program program = new Program();

                organisationUnit.setUid(event.getOrgUnit());
                program.setUid(event.getProgram());

                return Observable.zip(loadRulesEngine(program),
                        Single.create(new Single.OnSubscribe<List<User>>() {
                            @Override
                            public void call(SingleSubscriber<? super List<User>> singleSubscriber) {
                                try {
                                    singleSubscriber.onSuccess(eventInteractor.store().
                                            query(organisationUnit.getUid(), program.getUid()));
                                } catch (Exception e) {
                                    singleSubscriber.onError(e);
                                }
                            }
                        }),
                        new Func2<RuleEngine, List<Event>, Boolean>() {
                            @Override
                            public Boolean call(RuleEngine engine, List<Event> events) {
                                // assign rules engine
                                ruleEngine = engine;
                                currentEvent = event;

                                // clear events map
                                eventsMap.clear();

                                // put all existing events into map
                                eventsMap.putAll(toMap(eventInteractor.store().list(
                                        organisationUnit, program)));

                                // ruleEffectSubject = BehaviorSubject.create();
                                ruleEffectSubject = ReplaySubject.createWithSize(1);
                                ruleEffectSubject.subscribeOn(Schedulers.computation());
                                ruleEffectSubject.observeOn(AndroidSchedulers.mainThread());

                                return true;
                            }
                        });
            }
        });
    }

    public void notifyDataSetChanged() {
        if (currentEvent == null) {
            throw new IllegalArgumentException("No events are associated with RxRulesEngine");
        }

        // first, we need to find out this event in map and replace it
        if (eventsMap.containsKey(currentEvent.getUid())) {
            eventsMap.remove(currentEvent.getUid());
        }

        if (!subscription.isUnsubscribed()) {
            subscription.unsubscribe();
            subscription = new CompositeSubscription();
        }

        final String username = currentUserInteractor.username();
        subscription.add(eventInteractor.store().queryByUid(currentEvent.getUid())
                .switchMap(new Func1<Event, Observable<List<RuleEffect>>>() {
                    @Override
                    public Observable<List<RuleEffect>> call(Event event) {
                        logger.d(TAG, "Reloaded event: " + currentEvent.getUid());

                        currentEvent = event;
                        eventsMap.put(event.getUid(), event);

                        logger.d(TAG, "calculating rule effects");
                        // final Observable<List<RuleEffect>> ruleEffects = Observable.just();
                        List<RuleEffect> ruleEffects = ruleEngine.execute(
                                currentEvent, new ArrayList<>(eventsMap.values()));

                        // using zip in order to make sure that ruleEffects are successfully applied
                        // to event in database, only then pass formEntityActions down in the chain
                        // in order to apply them to view
                        Observable<Boolean> applyEffects = applyRuleEffects(
                                event, username, ruleEffects);
                        return Observable.zip(applyEffects, Observable.just(ruleEffects),
                                new Func2<Boolean, List<RuleEffect>, List<RuleEffect>>() {
                                    @Override
                                    public List<RuleEffect> call(Boolean isSuccess, List<RuleEffect> effects) {
                                        return effects;
                                    }
                                });
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<List<RuleEffect>>() {
                    @Override
                    public void call(List<RuleEffect> ruleEffects) {
                        logger.d(TAG, "Successfully computed new RuleEffects");
                        ruleEffectSubject.onNext(ruleEffects);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        logger.e(TAG, "Failed to process event", throwable);
                        ruleEffectSubject.onError(throwable);
                    }
                }));
    }

    public Observable<List<RuleEffect>> observable() {
        return ruleEffectSubject;
    }

    private Observable<RuleEngine> loadRulesEngine(Program program) {
        RuleEngine ruleEngine = new RuleEngine.Builder()
                .programRuleVariables(program.getProgramRuleVariables())
                .programRules(program.getProgramRules())
                .build();

        return Observable.create(ruleEngine);


        return Observable.zip(loadProgramRules(program), loadProgramRuleVariables(program),
                new Func2<List<ProgramRule>, List<ProgramRuleVariable>, RuleEngine>() {
                    @Override
                    public RuleEngine call(List<ProgramRule> programRules,
                                           List<ProgramRuleVariable> programRuleVariables) {
                        return new RuleEngine.Builder()
                                .programRuleVariables(programRuleVariables)
                                .programRules(programRules)
                                .build();
                    }
                });
    }

    private Observable<List<ProgramRule>> loadProgramRules(Program program) {
        isNull(program, "Program must not be null");
        List<ProgramRule> programRules;

        if (program.getProgramRules().isEmpty()) {
            programRules = new ArrayList<>();
        }
        return program.getProgramRules();


        return programInteractor.store().queryByUid(program.getUid())
                .map(new Func1<List<ProgramRule>, List<ProgramRule>>() {
                    @Override
                    public List<ProgramRule> call(List<ProgramRule> programRules) {
                        if (programRules == null) {
                            programRules = new ArrayList<>();
                        }

                        for (ProgramRule programRule : programRules) {
                            List<ProgramRuleAction> programRuleActions = programRuleActionInteractor
                                    .list(programRule).toBlocking().first();
                            programRule.setProgramRuleActions(programRuleActions);
                        }

                        return programRules;
                    }
                });
    }

    private Observable<List<ProgramRuleVariable>> loadProgramRuleVariables(Program program) {
        List<ProgramRuleVariable> programRuleVariables;
        if (program.getProgramRuleVariables() == null) {
            programRuleVariables = new ArrayList<>();
        } else {
            programRuleVariables = program.getProgramRuleVariables();
        }

        return Observable.create(programRuleVariables);
        return programRuleVariableInteractor.list(program)
                .map(new Func1<List<ProgramRuleVariable>, List<ProgramRuleVariable>>() {
                    @Override
                    public List<ProgramRuleVariable> call(List<ProgramRuleVariable> variables) {
                        if (variables == null) {
                            variables = new ArrayList<>();
                        }

                        return variables;
                    }
                });
    }


    private Observable<Boolean> applyRuleEffects(
            final Event event, final String username, final List<RuleEffect> ruleEffects) {
        return Observable.just(event)
                .switchMap(new Func1<Event, Observable<Boolean>>() {
                    @Override
                    public Observable<Boolean> call(Event event) {
                        if (ruleEffects == null || ruleEffects.isEmpty()) {
                            return Observable.just(true);
                        }

                        Map<String, TrackedEntityDataValue> dataValueMap = new HashMap<>();
                        if (event.getDataValues() != null && !event.getDataValues().isEmpty()) {
                            for (TrackedEntityDataValue entityDataValue : event.getDataValues()) {
                                dataValueMap.put(entityDataValue.getDataElement(), entityDataValue);
                            }
                        }

                        for (RuleEffect ruleEffect : ruleEffects) {
                            if (ProgramRuleActionType.ASSIGN.equals(
                                    ruleEffect.getProgramRuleActionType()) &&
                                    ruleEffect.getDataElement() != null) {

                                TrackedEntityDataValue dataValue = dataValueMap.get(
                                        ruleEffect.getDataElement().getUid());

                                // it can happen that event does not contain data value for yet
                                // for given ruleEffect, it means we need to create one
                                if (dataValue == null) {
                                    String dataElement = ruleEffect.getDataElement().getUid();

                                    dataValue = new TrackedEntityDataValue();
                                    dataValue.setDataElement(dataElement);
                                    dataValue.setStoredBy(username);
                                    dataValue.setEventUid(event.getUid());

                                    dataValueMap.put(dataElement, dataValue);
                                }

                                dataValue.setValue(ruleEffect.getData());
                            }
                        }

                        event.setDataValues(new ArrayList<>(dataValueMap.values()));
                        return eventInteractor.store().save(event);
                    }
                });
    }
}
