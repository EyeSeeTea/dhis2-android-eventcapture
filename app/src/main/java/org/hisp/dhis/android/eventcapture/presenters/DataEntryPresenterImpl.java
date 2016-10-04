package org.hisp.dhis.android.eventcapture.presenters;

import org.hisp.dhis.android.eventcapture.model.RxRulesEngine;
import org.hisp.dhis.android.eventcapture.views.DataEntryView;
import org.hisp.dhis.client.sdk.core.EventInteractor;
import org.hisp.dhis.client.sdk.core.OptionSetInteractor;
import org.hisp.dhis.client.sdk.core.ProgramInteractor;
import org.hisp.dhis.client.sdk.core.TrackedEntityDataValueInteractor;
import org.hisp.dhis.client.sdk.core.UserInteractor;
import org.hisp.dhis.client.sdk.models.dataelement.DataElement;
import org.hisp.dhis.client.sdk.models.event.Event;
import org.hisp.dhis.client.sdk.models.option.Option;
import org.hisp.dhis.client.sdk.models.option.OptionSet;
import org.hisp.dhis.client.sdk.models.program.Program;
import org.hisp.dhis.client.sdk.models.program.ProgramStage;
import org.hisp.dhis.client.sdk.models.program.ProgramStageDataElement;
import org.hisp.dhis.client.sdk.models.program.ProgramStageSection;
import org.hisp.dhis.client.sdk.models.trackedentity.TrackedEntityDataValue;
import org.hisp.dhis.client.sdk.rules.RuleEffect;
import org.hisp.dhis.client.sdk.ui.bindings.commons.RxOnValueChangedListener;
import org.hisp.dhis.client.sdk.ui.bindings.commons.RxUtils;
import org.hisp.dhis.client.sdk.ui.bindings.views.View;
import org.hisp.dhis.client.sdk.ui.models.FormEntity;
import org.hisp.dhis.client.sdk.ui.models.FormEntityAction;
import org.hisp.dhis.client.sdk.ui.models.FormEntityAction.FormEntityActionType;
import org.hisp.dhis.client.sdk.ui.models.FormEntityCharSequence;
import org.hisp.dhis.client.sdk.ui.models.FormEntityCheckBox;
import org.hisp.dhis.client.sdk.ui.models.FormEntityDate;
import org.hisp.dhis.client.sdk.ui.models.FormEntityEditText;
import org.hisp.dhis.client.sdk.ui.models.FormEntityEditText.InputType;
import org.hisp.dhis.client.sdk.ui.models.FormEntityFilter;
import org.hisp.dhis.client.sdk.ui.models.FormEntityRadioButtons;
import org.hisp.dhis.client.sdk.ui.models.FormEntityText;
import org.hisp.dhis.client.sdk.ui.models.Picker;
import org.hisp.dhis.client.sdk.utils.Logger;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

import static org.hisp.dhis.client.sdk.utils.Preconditions.isNull;
import static org.hisp.dhis.client.sdk.utils.StringUtils.isEmpty;


public class DataEntryPresenterImpl implements DataEntryPresenter {
    private static final String TAG = DataEntryPresenterImpl.class.getSimpleName();

    private final UserInteractor currentUserInteractor;

    private final OptionSetInteractor optionSetInteractor;
    private final ProgramInteractor programInteractor;
    private final EventInteractor eventInteractor;
    private final TrackedEntityDataValueInteractor dataValueInteractor;

    private final RxRulesEngine rxRulesEngine;

    private final Logger logger;
    private final RxOnValueChangedListener onValueChangedListener;

    private DataEntryView dataEntryView;
    private CompositeSubscription subscription;


    public DataEntryPresenterImpl(UserInteractor currentUserInteractor,
                                  ProgramInteractor programInteractor,
                                  OptionSetInteractor optionSetInteractor,
                                  EventInteractor eventInteractor,
                                  TrackedEntityDataValueInteractor dataValueInteractor,
                                  RxRulesEngine rxRulesEngine, Logger logger) {
        this.currentUserInteractor = currentUserInteractor;
        this.optionSetInteractor = optionSetInteractor;
        this.programInteractor = programInteractor;

        this.eventInteractor = eventInteractor;
        this.dataValueInteractor = dataValueInteractor;
        this.rxRulesEngine = rxRulesEngine;

        this.logger = logger;
        this.onValueChangedListener = new RxOnValueChangedListener();
    }

    @Override
    public void attachView(View view) {
        isNull(view, "view must not be null");
        dataEntryView = (DataEntryView) view;
    }

    @Override
    public void detachView() {
        dataEntryView = null;

        if (subscription != null && !subscription.isUnsubscribed()) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    @Override
    public void createDataEntryFormStage(final String eventId, final String programId, final String programStageId) {
        logger.d(TAG, "ProgramStageId: " + programStageId);

        if (subscription != null && !subscription.isUnsubscribed()) {
            subscription.unsubscribe();
            subscription = null;
        }

        final String username = currentUserInteractor.username();

        subscription = new CompositeSubscription();
        subscription.add(engine().take(1).switchMap(
                new Func1<List<FormEntityAction>, Observable<SimpleEntry<List<FormEntity>, List<FormEntityAction>>>>() {
                    @Override
                    public Observable<SimpleEntry<List<FormEntity>, List<FormEntityAction>>> call(
                            final List<FormEntityAction> formEntityActions) {
                        return Observable.zip(eventInteractor.store().get(eventId), RxUtils.single(programInteractor.store().get(programId)),
                                new Func2<Event, Program, SimpleEntry<List<FormEntity>, List<FormEntityAction>>>() {

                                    @Override
                                    public SimpleEntry<List<FormEntity>, List<FormEntityAction>> call(
                                            Event event, Program program) {
                                        ProgramStage currentProgramStage = null;
                                        for (ProgramStage programStage : program.getProgramStages()) {
                                            if(programStage.getUid().equals(programStageId)) {
                                                currentProgramStage = programStage;
                                            }
                                        }

                                        if(currentProgramStage == null) {
                                            throw new IllegalArgumentException("No program stage uid found for programStageId: " + programStageId);
                                        }
                                        List<ProgramStageDataElement> dataElements =
                                                currentProgramStage.getProgramStageDataElements();
                                        List<FormEntity> formEntities = transformDataElements(
                                                username, event, dataElements);

                                        return new SimpleEntry<>(formEntities, formEntityActions);
                                    }
                                });
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<SimpleEntry<List<FormEntity>, List<FormEntityAction>>>() {
                    @Override
                    public void call(SimpleEntry<List<FormEntity>, List<FormEntityAction>> result) {
                        if (dataEntryView != null) {
                            dataEntryView.showDataEntryForm(result.getKey(), result.getValue());
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        logger.e(TAG, "Something went wrong during form construction", throwable);
                    }
                }));

        subscription.add(saveTrackedEntityDataValues());
        subscription.add(subscribeToEngine());
    }

    @Override
    public void createDataEntryFormSection(final String eventId,
                                           final String programId,
                                           final String programStageId,
                                           final String programStageSectionId) {
        logger.d(TAG, "ProgramStageSectionId: " + programStageSectionId);

        if (subscription != null && !subscription.isUnsubscribed()) {
            subscription.unsubscribe();
            subscription = null;
        }

        final String username = currentUserInteractor.username();

        subscription = new CompositeSubscription();
        subscription.add(engine().take(1).switchMap(
                new Func1<List<FormEntityAction>, Observable<SimpleEntry<List<FormEntity>, List<FormEntityAction>>>>() {
                    @Override
                    public Observable<SimpleEntry<List<FormEntity>, List<FormEntityAction>>> call(
                            final List<FormEntityAction> formEntityActions) {
                        return Observable.zip(eventInteractor.store().get(eventId), RxUtils.single(programInteractor.store().get(programId)),
                                new Func2<Event, Program, SimpleEntry<List<FormEntity>, List<FormEntityAction>>>() {

                                    @Override
                                    public SimpleEntry<List<FormEntity>, List<FormEntityAction>> call(
                                            Event event, Program program) {
                                        ProgramStage currentProgramStage = null;
                                        ProgramStageSection currentProgramStageSection = null;

                                        for (ProgramStage stage : program.getProgramStages()) {
                                            if(stage.getUid().equals(programStageId)) {
                                                currentProgramStage = stage;
                                            }
                                        }

                                        if(currentProgramStage == null) {
                                            throw new IllegalArgumentException("No program stage found for programStageId: " + programStageId);
                                        }

                                        for (ProgramStageSection stageSection : currentProgramStage.getProgramStageSections()) {
                                            if(stageSection.getUid().equals(programStageSectionId)) {
                                                currentProgramStageSection = stageSection;
                                            }
                                        }

                                        if(currentProgramStageSection == null) {
                                            throw new IllegalArgumentException("No program stage section found for programStageSectionId: " + programStageSectionId);
                                        }
                                        List<ProgramStageDataElement> dataElements =
                                                currentProgramStageSection.getProgramStageDataElements();

                                        // sort ProgramStageDataElements by sortOrder
                                        if (dataElements != null) {
                                            Collections.sort(dataElements,
                                                    ProgramStageDataElement.SORT_ORDER_COMPARATOR);
                                        }

                                        List<FormEntity> formEntities = transformDataElements(
                                                username, event, dataElements);
                                        return new SimpleEntry<>(formEntities, formEntityActions);
                                    }
                                });
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<SimpleEntry<List<FormEntity>, List<FormEntityAction>>>() {
                    @Override
                    public void call(SimpleEntry<List<FormEntity>, List<FormEntityAction>> entry) {
                        if (dataEntryView != null) {
                            dataEntryView.showDataEntryForm(entry.getKey(), entry.getValue());
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        logger.e(TAG, "Something went wrong during form construction", throwable);
                    }
                }));

        subscription.add(saveTrackedEntityDataValues());
        subscription.add(subscribeToEngine());
    }

    private Observable<List<FormEntityAction>> engine() {
        return rxRulesEngine.observable()
                .map(new Func1<List<RuleEffect>, List<FormEntityAction>>() {
                    @Override
                    public List<FormEntityAction> call(List<RuleEffect> effects) {
                        return transformRuleEffects(effects);
                    }
                });
    }

    private Subscription subscribeToEngine() {
        return engine().subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<List<FormEntityAction>>() {
                    @Override
                    public void call(List<FormEntityAction> actions) {
                        if (dataEntryView != null) {
                            dataEntryView.updateDataEntryForm(actions);
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        logger.e(TAG, "Failed to calculate rules", throwable);
                    }
                });
    }

    private Subscription saveTrackedEntityDataValues() {
        return Observable.create(onValueChangedListener)
                .debounce(512, TimeUnit.MILLISECONDS)
                .switchMap(new Func1<FormEntity, Observable<Boolean>>() {
                    @Override
                    public Observable<Boolean> call(FormEntity formEntity) {
                        return onFormEntityChanged(formEntity);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Boolean>() {
                    @Override
                    public void call(Boolean isSaved) {
                        if (isSaved) {
                            logger.d(TAG, "data value is saved successfully");

                            // fire rule engine execution
                            rxRulesEngine.notifyDataSetChanged();
                        } else {
                            logger.d(TAG, "Failed to save value");
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        logger.e(TAG, "Failed to save value", throwable);
                    }
                });
    }

    private List<FormEntity> transformDataElements(
            String username, Event event, List<ProgramStageDataElement> stageDataElements) {
        if (stageDataElements == null || stageDataElements.isEmpty()) {
            return new ArrayList<>();
        }

        // List<DataElement> dataElements = new ArrayList<>();
        for (ProgramStageDataElement stageDataElement : stageDataElements) {
            DataElement dataElement = stageDataElement.getDataElement();
            if (dataElement == null) {
                throw new RuntimeException("Malformed metadata: Program" +
                        "StageDataElement " + stageDataElement.getUid() +
                        " does not have reference to DataElement");
            }

            OptionSet optionSet = dataElement.getOptionSet();
            if (optionSet != null) {
                List<Option> options = optionSetInteractor.store().get(
                        dataElement.getOptionSet().getUid()).getOptions();
                optionSet.setOptions(options);
            }
        }

        Map<String, TrackedEntityDataValue> dataValueMap = new HashMap<>();
        if (event.getDataValues() != null && !event.getDataValues().isEmpty()) {
            for (TrackedEntityDataValue dataValue : event.getDataValues()) {
                dataValueMap.put(dataValue.getDataElement(), dataValue);
            }
        }

        List<FormEntity> formEntities = new ArrayList<>();
        for (ProgramStageDataElement stageDataElement : stageDataElements) {
            DataElement dataElement = stageDataElement.getDataElement();
            formEntities.add(transformDataElement(
                    username, event, dataValueMap.get(dataElement.getUid()), stageDataElement));
        }

        return formEntities;
    }

    private FormEntity transformDataElement(String username, Event event,
                                            TrackedEntityDataValue dataValue,
                                            ProgramStageDataElement stageDataElement) {
        DataElement dataElement = stageDataElement.getDataElement();

        // logger.d(TAG, "DataElement: " + dataElement.getDisplayName());
        // logger.d(TAG, "ValueType: " + dataElement.getValueType());

        // create TrackedEntityDataValue upfront
        if (dataValue == null) {
            dataValue = new TrackedEntityDataValue();
            dataValue.setEventUid(event.getUid());
            dataValue.setDataElement(dataElement.getUid());
            dataValue.setStoredBy(username);
        }

        // logger.d(TAG, "transformDataElement() -> TrackedEntityDataValue: " +
        //        dataValue + " localId: " + dataValue.getId());

        // in case if we have option set linked to data-element, we
        // need to process it regardless of data-element value type
        if (dataElement.getOptionSet() != null) {
            List<Option> options = dataElement.getOptionSet().getOptions();

            Picker picker = new Picker.Builder()
                    .hint(dataElement.getDisplayName())
                    .build();
            if (options != null && !options.isEmpty()) {
                for (Option option : options) {
                    Picker childPicker = new Picker.Builder()
                            .id(option.getCode())
                            .name(option.getDisplayName())
                            .parent(picker)
                            .build();
                    picker.addChild(childPicker);

                    if (option.getCode().equals(dataValue.getValue())) {
                        picker.setSelectedChild(childPicker);
                    }
                }
            }

            FormEntityFilter formEntityFilter = new FormEntityFilter(dataElement.getUid(),
                    getFormEntityLabel(stageDataElement), dataValue);
            formEntityFilter.setPicker(picker);
            formEntityFilter.setOnFormEntityChangeListener(onValueChangedListener);

            return formEntityFilter;
        }

        switch (dataElement.getValueType()) {
            case TEXT: {
                FormEntityEditText formEntityEditText = new FormEntityEditText(dataElement.getUid(),
                        getFormEntityLabel(stageDataElement), InputType.TEXT, dataValue);
                formEntityEditText.setValue(dataValue.getValue(), false);
                formEntityEditText.setOnFormEntityChangeListener(onValueChangedListener);
                return formEntityEditText;
            }
            case LONG_TEXT: {
                FormEntityEditText formEntityEditText = new FormEntityEditText(dataElement.getUid(),
                        getFormEntityLabel(stageDataElement), InputType.LONG_TEXT, dataValue);
                formEntityEditText.setValue(dataValue.getValue(), false);
                formEntityEditText.setOnFormEntityChangeListener(onValueChangedListener);
                return formEntityEditText;
            }
            case PHONE_NUMBER: {
                FormEntityEditText formEntityEditText = new FormEntityEditText(dataElement.getUid(),
                        getFormEntityLabel(stageDataElement), InputType.TEXT, dataValue);
                formEntityEditText.setValue(dataValue.getValue(), false);
                formEntityEditText.setOnFormEntityChangeListener(onValueChangedListener);
                return formEntityEditText;
            }
            case EMAIL: {
                FormEntityEditText formEntityEditText = new FormEntityEditText(dataElement.getUid(),
                        getFormEntityLabel(stageDataElement), InputType.TEXT, dataValue);
                formEntityEditText.setValue(dataValue.getValue(), false);
                formEntityEditText.setOnFormEntityChangeListener(onValueChangedListener);
                return formEntityEditText;
            }
            case NUMBER: {
                FormEntityEditText formEntityEditText = new FormEntityEditText(dataElement.getUid(),
                        getFormEntityLabel(stageDataElement), InputType.NUMBER, dataValue);
                formEntityEditText.setValue(dataValue.getValue(), false);
                formEntityEditText.setOnFormEntityChangeListener(onValueChangedListener);
                return formEntityEditText;
            }
            case INTEGER: {
                FormEntityEditText formEntityEditText = new FormEntityEditText(dataElement.getUid(),
                        getFormEntityLabel(stageDataElement), InputType.INTEGER, dataValue);
                formEntityEditText.setValue(dataValue.getValue(), false);
                formEntityEditText.setOnFormEntityChangeListener(onValueChangedListener);
                return formEntityEditText;
            }
            case INTEGER_POSITIVE: {
                FormEntityEditText formEntityEditText = new FormEntityEditText(dataElement.getUid(),
                        getFormEntityLabel(stageDataElement), InputType.INTEGER_POSITIVE, dataValue);
                formEntityEditText.setValue(dataValue.getValue(), false);
                formEntityEditText.setOnFormEntityChangeListener(onValueChangedListener);
                return formEntityEditText;
            }
            case INTEGER_NEGATIVE: {
                FormEntityEditText formEntityEditText = new FormEntityEditText(dataElement.getUid(),
                        getFormEntityLabel(stageDataElement), InputType.INTEGER_NEGATIVE, dataValue);
                formEntityEditText.setValue(dataValue.getValue(), false);
                formEntityEditText.setOnFormEntityChangeListener(onValueChangedListener);
                return formEntityEditText;
            }
            case INTEGER_ZERO_OR_POSITIVE: {
                FormEntityEditText formEntityEditText = new FormEntityEditText(dataElement.getUid(),
                        getFormEntityLabel(stageDataElement), InputType.INTEGER_ZERO_OR_POSITIVE, dataValue);
                formEntityEditText.setValue(dataValue.getValue(), false);
                formEntityEditText.setOnFormEntityChangeListener(onValueChangedListener);
                return formEntityEditText;
            }
            case DATE: {
                FormEntityDate formEntityDate = new FormEntityDate(dataElement.getUid(),
                        getFormEntityLabel(stageDataElement), dataValue);
                formEntityDate.setValue(dataValue.getValue(), false);
                formEntityDate.setOnFormEntityChangeListener(onValueChangedListener);
                return formEntityDate;
            }
            case BOOLEAN: {
                FormEntityRadioButtons formEntityRadioButtons = new FormEntityRadioButtons(
                        dataElement.getUid(), getFormEntityLabel(stageDataElement), dataValue);
                formEntityRadioButtons.setValue(dataValue.getValue(), false);
                formEntityRadioButtons.setOnFormEntityChangeListener(onValueChangedListener);
                return formEntityRadioButtons;
            }
            case TRUE_ONLY: {
                FormEntityCheckBox formEntityCheckBox = new FormEntityCheckBox(
                        dataElement.getUid(), getFormEntityLabel(stageDataElement), dataValue);
                formEntityCheckBox.setValue(dataValue.getValue(), false);
                formEntityCheckBox.setOnFormEntityChangeListener(onValueChangedListener);
                return formEntityCheckBox;
            }
            default:
                logger.d(TAG, "Unsupported FormEntity type: " + dataElement.getValueType());

                FormEntityText formEntityText = new FormEntityText(dataElement.getUid(),
                        getFormEntityLabel(stageDataElement));
                formEntityText.setValue("Unsupported value type: " +
                        dataElement.getValueType(), false);

                return formEntityText;
        }
    }

    private List<FormEntityAction> transformRuleEffects(List<RuleEffect> ruleEffects) {
        List<FormEntityAction> entityActions = new ArrayList<>();
        if (ruleEffects == null || ruleEffects.isEmpty()) {
            return entityActions;
        }

        for (RuleEffect ruleEffect : ruleEffects) {
            if (ruleEffect == null || ruleEffect.getProgramRuleActionType() == null) {
                logger.d(TAG, "failed processing broken RuleEffect");
                continue;
            }

            switch (ruleEffect.getProgramRuleActionType()) {
                case HIDEFIELD: {
                    if (ruleEffect.getDataElement() != null) {
                        String dataElementUid = ruleEffect.getDataElement().getUid();
                        FormEntityAction formEntityAction = new FormEntityAction(
                                dataElementUid, null, FormEntityActionType.HIDE);
                        entityActions.add(formEntityAction);
                    }
                    break;
                }
                case ASSIGN: {
                    if (ruleEffect.getDataElement() != null) {
                        String dataElementUid = ruleEffect.getDataElement().getUid();
                        FormEntityAction formEntityAction = new FormEntityAction(
                                dataElementUid, ruleEffect.getData(), FormEntityActionType.ASSIGN);
                        entityActions.add(formEntityAction);
                    }
                    break;
                }
            }
        }

        return entityActions;
    }

    private String getFormEntityLabel(ProgramStageDataElement stageDataElement) {
        DataElement dataElement = stageDataElement.getDataElement();
        String label = isEmpty(dataElement.getDisplayFormName()) ?
                dataElement.getDisplayName() : dataElement.getDisplayFormName();

        if (stageDataElement.isCompulsory()) {
            label = label + " (*)";
        }

        return label;
    }

    private Observable<Boolean> onFormEntityChanged(FormEntity formEntity) {
        return dataValueInteractor.save(mapFormEntityToDataValue(formEntity));
    }

    private TrackedEntityDataValue mapFormEntityToDataValue(FormEntity entity) {
        if (entity instanceof FormEntityFilter) {
            Picker picker = ((FormEntityFilter) entity).getPicker();

            String value = "";
            if (picker != null && picker.getSelectedChild() != null) {
                value = picker.getSelectedChild().getId();
            }

            TrackedEntityDataValue dataValue;
            if (entity.getTag() != null) {
                dataValue = (TrackedEntityDataValue) entity.getTag();
            } else {
                throw new IllegalArgumentException("TrackedEntityDataValue must be " +
                        "assigned to FormEntity upfront");
            }

            dataValue.setValue(value);

            logger.d(TAG, "New value " + value + " is emitted for " + entity.getLabel());

            return dataValue;
        } else if (entity instanceof FormEntityCharSequence) {
            String value = ((FormEntityCharSequence) entity).getValue().toString();

            TrackedEntityDataValue dataValue;
            if (entity.getTag() != null) {
                dataValue = (TrackedEntityDataValue) entity.getTag();
            } else {
                throw new IllegalArgumentException("TrackedEntityDataValue must be " +
                        "assigned to FormEntity upfront");
            }

            dataValue.setValue(value);

            logger.d(TAG, "New value " + value + " is emitted for " + entity.getLabel());

            return dataValue;
        }

        return null;
    }
}
