/*
 * Copyright (c) 2016, University of Oslo
 *
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.hisp.dhis.android.eventcapture.presenters;

import org.hisp.dhis.android.eventcapture.model.SyncWrapper;
import org.hisp.dhis.android.eventcapture.views.SelectorView;
import org.hisp.dhis.client.sdk.core.ModelUtils;
import org.hisp.dhis.client.sdk.core.commons.ApiException;
import org.hisp.dhis.client.sdk.core.event.EventInteractor;
import org.hisp.dhis.client.sdk.core.organisationunit.OrganisationUnitInteractor;
import org.hisp.dhis.client.sdk.core.program.ProgramInteractor;
import org.hisp.dhis.client.sdk.models.common.State;
import org.hisp.dhis.client.sdk.models.dataelement.DataElement;
import org.hisp.dhis.client.sdk.models.event.Event;
import org.hisp.dhis.client.sdk.models.event.EventStatus;
import org.hisp.dhis.client.sdk.models.organisationunit.OrganisationUnit;
import org.hisp.dhis.client.sdk.models.program.Program;
import org.hisp.dhis.client.sdk.models.program.ProgramStage;
import org.hisp.dhis.client.sdk.models.program.ProgramStageDataElement;
import org.hisp.dhis.client.sdk.models.program.ProgramType;
import org.hisp.dhis.client.sdk.models.trackedentity.TrackedEntityDataValue;
import org.hisp.dhis.client.sdk.ui.bindings.commons.ApiExceptionHandler;
import org.hisp.dhis.client.sdk.ui.bindings.commons.AppError;
import org.hisp.dhis.client.sdk.ui.bindings.commons.SessionPreferences;
import org.hisp.dhis.client.sdk.ui.bindings.commons.SyncDateWrapper;
import org.hisp.dhis.client.sdk.ui.bindings.views.View;
import org.hisp.dhis.client.sdk.ui.models.Picker;
import org.hisp.dhis.client.sdk.ui.models.ReportEntity;
import org.hisp.dhis.client.sdk.ui.models.ReportEntityFilter;
import org.hisp.dhis.client.sdk.utils.Logger;

import java.net.HttpURLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

import static org.hisp.dhis.client.sdk.utils.Preconditions.isNull;
import static org.hisp.dhis.client.sdk.utils.StringUtils.isEmpty;

public class SelectorPresenterImpl implements SelectorPresenter {
    private static final String TAG = SelectorPresenterImpl.class.getSimpleName();
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private final OrganisationUnitInteractor organisationUnitInteractor;
    private final ProgramInteractor programInteractor;
    private final EventInteractor eventInteractor;
    private final SessionPreferences sessionPreferences;
    private final SyncDateWrapper syncDateWrapper;
    private final ApiExceptionHandler apiExceptionHandler;
    private final SyncWrapper syncWrapper;
    private final Logger logger;
    private CompositeSubscription subscription;
    private boolean hasSyncedBefore;
    private SelectorView selectorView;
    private boolean isSyncing;
    private final SimpleDateFormat simpleDateFormat;
    private ArrayList reportEntityDataElementFilter;

    public SelectorPresenterImpl(OrganisationUnitInteractor interactor,
                                 ProgramInteractor programInteractor,
                                 EventInteractor eventInteractor,
                                 SessionPreferences sessionPreferences,
                                 SyncDateWrapper syncDateWrapper,
                                 SyncWrapper syncWrapper,
                                 ApiExceptionHandler apiExceptionHandler,
                                 Logger logger) {
        this.organisationUnitInteractor = interactor;
        this.programInteractor = programInteractor;
        this.eventInteractor = eventInteractor;
        this.sessionPreferences = sessionPreferences;
        this.syncDateWrapper = syncDateWrapper;
        this.syncWrapper = syncWrapper;
        this.apiExceptionHandler = apiExceptionHandler;
        this.logger = logger;
        this.simpleDateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.US);

        this.subscription = new CompositeSubscription();
        this.hasSyncedBefore = false;
    }

    private static void traverseAndSetDefaultSelection(Picker tree) {
        if (tree != null) {

            Picker node = tree;
            do {
                if (node.getChildren().size() == 1) {
                    // get the only child node and set it as selected
                    Picker singleChild = node.getChildren().get(0);
                    node.setSelectedChild(singleChild);
                }
            } while ((node = node.getSelectedChild()) != null);
        }
    }

    public void attachView(View view) {
        isNull(view, "SelectorView must not be null");

        selectorView = (SelectorView) view;

        if (isSyncing) {
            selectorView.showProgressBar();
        } else {
            selectorView.hideProgressBar();
        }

        // check if metadata was synced,
        // if not, sync it
        if (!isSyncing && !hasSyncedBefore) {
            sync();
        }

        listPickers();
    }

    @Override
    public void detachView() {
        selectorView.hideProgressBar();
        selectorView = null;
    }

    @Override
    public void onPickersSelectionsChanged(List<Picker> pickerList) {
        if (pickerList != null) {
            sessionPreferences.clearSelectedPickers();
            for (int index = 0; index < pickerList.size(); index++) {
                Picker current = pickerList.get(index);
                Picker child = current.getSelectedChild();
                if (child == null) { //done with pickers. exit.
                    return;
                }
                String pickerId = child.getId();
                sessionPreferences.setSelectedPickerUid(index, pickerId);
            }
        }
    }

    @Override
    public void sync() {
        selectorView.showProgressBar();
        isSyncing = true;
        subscription.add(syncWrapper.syncMetaData()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<List<Program>>() {
                    @Override
                    public void call(List<Program> programs) {
                        isSyncing = false;
                        hasSyncedBefore = true;

                        if (selectorView != null) {
                            selectorView.hideProgressBar();
                        }
                        listPickers();
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        isSyncing = false;
                        hasSyncedBefore = true;
                        if (selectorView != null) {
                            selectorView.hideProgressBar();
                        }
                        handleError(throwable);
                    }
                }));
        subscription.add(syncWrapper.syncData()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<List<Event>>() {
                    @Override
                    public void call(List<Event> events) {
                        listPickers();

                        logger.d(TAG, "Synced events successfully");
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        logger.e(TAG, "Failed to sync events", throwable);
                    }
                }));
    }

    @Override
    public void listPickers() {
        logger.d(TAG, "listPickers()");
        subscription.add(Observable.zip(
                getOrganisationUnits(),
                getPrograms(),
                new Func2<List<OrganisationUnit>, List<Program>, Picker>() {
                    @Override
                    public Picker call(List<OrganisationUnit> units, List<Program> programs) {
                        return createPickerTree(units, programs);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Picker>() {
                    @Override
                    public void call(Picker picker) {
                        if (selectorView != null) {
                            selectorView.showPickers(picker);
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        logger.e(TAG, "Failed listing pickers.", throwable);
                    }
                }));
    }

    @Override
    public void listEvents(String organisationUnitId, final String programId) {
        final OrganisationUnit orgUnit = new OrganisationUnit();

        orgUnit.setUid(organisationUnitId);

        subscription.add(getProgram(programId)
                .switchMap(new Func1<Program, Observable<List<ReportEntity>>>() {
                    @Override
                    public Observable<List<ReportEntity>> call(Program program) {
                        if (program == null) {
                            throw new IllegalArgumentException(
                                    "Program id doesn't exist");
                        }
                        ProgramStage programStage = null;
                        if(ProgramType.WITHOUT_REGISTRATION.equals(program.getProgramType())) {
                            programStage = program.getProgramStages().get(0);
                        }

                        if(programStage == null) {
                            throw new IllegalArgumentException("No stages found for program");
                        }


                        Observable stageElementsObservable = Observable.just(programStage.getProgramStageDataElements());


                        return Observable.zip(stageElementsObservable, listEventsByOrgUnitProgram(orgUnit,program),
                                new Func2<List<ProgramStageDataElement>, List<Event>, List<ReportEntity>>() {

                                    @Override
                                    public List<ReportEntity> call(List<ProgramStageDataElement> stageDataElements,
                                                                   List<Event> events) {
                                        reportEntityDataElementFilter = sessionPreferences.getReportEntityDataModelFilters(
                                                programId,
                                                mapDataElementNameToDefaultViewSetting(stageDataElements));
                                        return transformEvents(stageDataElements, events);
                                    }
                                });
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<List<ReportEntity>>() {
                    @Override
                    public void call(List<ReportEntity> reportEntities) {

                        if (selectorView != null) {
                            selectorView.setReportEntityLabelFilters(reportEntityDataElementFilter);
                            selectorView.showFilterOptionItem(true);
                            selectorView.showReportEntities(reportEntities);
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        if (selectorView != null) {
                            selectorView.showFilterOptionItem(false);
                        }
                        logger.e(TAG, "Failed loading events", throwable);
                    }
                }));
    }

    @Override
    public void createEvent(final String orgUnitId, final String programId) {

        subscription.add(getProgram(programId)
                .map(new Func1<Program, ProgramStage>() {
                    @Override
                    public ProgramStage call(Program program) {
                        if (program != null && ProgramType.WITHOUT_REGISTRATION.equals(program.getProgramType())) {
                            return program.getProgramStages().get(0);
                        }
                        return null;
                    }
                })
                .map(new Func1<ProgramStage, Event>() {
                    @Override
                    public Event call(ProgramStage programStage) {
                        if (programStage == null) {
                            throw new IllegalArgumentException("In order to create event, " +
                                    "we need program stage to be in place");
                        }
                        Event event = new Event();
                        event.setOrgUnit(orgUnitId);
                        event.setProgram(programId);
                        event.setProgramStage(programStage.getUid());
                        event.setStatus(EventStatus.ACTIVE);

                        String eventDateString = Calendar.getInstance().getTime().toString();
                        Date eventDate = null;
                        try {
                             eventDate = simpleDateFormat.parse(eventDateString);
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }

                        event.setEventDate(eventDate);

                        eventInteractor.store().save(event);
                        return event;
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Event>() {
                    @Override
                    public void call(Event event) {
                        if (selectorView != null) {
                            selectorView.navigateToFormSectionActivity(event.getUid(), event.getProgram());
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        logger.e(TAG, "Failed creating event", throwable);
                    }
                })
        );
    }

    @Override
    public void deleteEvent(final ReportEntity reportEntity) {
        subscription.add(getEvent(reportEntity.getId())
                .switchMap(new Func1<Event, Observable<Boolean>>() {
                    @Override
                    public Observable<Boolean> call(Event event) {
                        int eventDeleted = eventInteractor.store().delete(event);

                        if(eventDeleted > 0) {
                            return Observable.just(true);
                        }
                        else return Observable.just(false);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Boolean>() {
                    @Override
                    public void call(Boolean aBoolean) {
                        logger.d(TAG, "Event deleted");
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        logger.e(TAG, "Error deleting event: " + reportEntity, throwable);
                        if (selectorView != null) {
                            selectorView.onReportEntityDeletionError(reportEntity);
                        }
                    }
                }));
    }

    @Override
    public void handleError(final Throwable throwable) {
        AppError error = apiExceptionHandler.handleException(TAG, throwable);

        if (throwable instanceof ApiException) {
            ApiException exception = (ApiException) throwable;

            if (exception.getResponse() != null) {
                switch (exception.getResponse().code()) {
                    case HttpURLConnection.HTTP_UNAUTHORIZED: {
                        selectorView.showError(error.getDescription());
                        break;
                    }
                    case HttpURLConnection.HTTP_NOT_FOUND: {
                        selectorView.showError(error.getDescription());
                        break;
                    }
                    default: {
                        selectorView.showUnexpectedError(error.getDescription());
                        break;
                    }
                }
            }
        } else {
            logger.e(TAG, "handleError", throwable);
        }
    }

    @Override
    public void setReportEntityDataElementFilters(String programId, ArrayList<ReportEntityFilter> filters) {
        sessionPreferences.setReportEntityDataModelFilters(programId, filters);
    }

    private List<ReportEntity> transformEvents(List<ProgramStageDataElement> dataElements,
                                               List<Event> events) {

        // preventing additional work
        if (events == null || events.isEmpty()) {
            return new ArrayList<>();
        }

        // sort events by eventDate
        Collections.sort(events, Event.DATE_COMPARATOR);
        Collections.reverse(events);

        // retrieve state map for given events
        // it is done synchronously
//        Map<Long, State> stateMap = eventInteractor.store().queryAll(events)
//                .toBlocking().first();
        List<ReportEntity> reportEntities = new ArrayList<>();

        for (Event event : events) {
            // status of event
            ReportEntity.Status status;
            // get state of event from database
            State state = event.getState();
            // State state = eventInteractor.get(event).toBlocking().first();

            logger.d(TAG, "State action for event " + event + " is " + state.toString());
            switch (event.getState()) {
                case SYNCED: {
                    status = ReportEntity.Status.SENT;
                    break;
                }
                case TO_POST: {
                    status = ReportEntity.Status.TO_POST;
                    break;
                }
                case TO_UPDATE: {
                    status = ReportEntity.Status.TO_UPDATE;
                    break;
                }
                case ERROR: {
                    status = ReportEntity.Status.ERROR;
                    break;
                }
                default: {
                    throw new IllegalArgumentException(
                            "Unsupported event state: " + state.toString());
                }
            }

            Map<String, String> dataElementToValueMap =
                    mapDataElementToValue(event.getDataValues());

            dataElementToValueMap.put(Event.EVENT_DATE_KEY,
                    event.getEventDate().toString());
            dataElementToValueMap.put(Event.STATUS_KEY, event.getStatus().toString());

            reportEntities.add(
                    new ReportEntity(
                            event.getUid(),
                            status,
                            dataElementToValueMap));
        }
        return reportEntities;
    }

    private ArrayList<ReportEntityFilter> mapDataElementNameToDefaultViewSetting(
            List<ProgramStageDataElement> dataElements) {

        ArrayList<ReportEntityFilter> defaultFilters = new ArrayList<>();
        defaultFilters.add(new ReportEntityFilter(Event.EVENT_DATE_KEY, Event.EVENT_DATE_LABEL, true));
        defaultFilters.add(new ReportEntityFilter(Event.STATUS_KEY, Event.STATUS_LABEL, true));

        if (dataElements != null && !dataElements.isEmpty()) {
            for (ProgramStageDataElement programStageDataElement : dataElements) {
                DataElement dataElement = programStageDataElement.getDataElement();
                String dataElementName = dataElement.getFormName();
                //if getFormName is empty use getDisplayName instead:
                if (dataElementName == null || dataElementName.isEmpty()) {
                    dataElementName = dataElement.getDisplayName();
                }
                boolean defaultViewSetting = programStageDataElement.isDisplayInReports();
                defaultFilters.add(new ReportEntityFilter(dataElement.getUid(), dataElementName, defaultViewSetting));
            }
        }
        return defaultFilters;
    }

    private Map<String, String> mapDataElementToValue(List<TrackedEntityDataValue> dataValues) {

        Map<String, String> dataElementToValueMap = new HashMap<>();

        if (dataValues != null && !dataValues.isEmpty()) {
            for (TrackedEntityDataValue dataValue : dataValues) {

                String value = !isEmpty(dataValue.getValue()) ? dataValue.getValue() : "";
                dataElementToValueMap.put(dataValue.getDataElement(), value);
            }
        }
        return dataElementToValueMap;
    }

    /*
     * Goes through given organisation units and programs and builds Picker tree
     */
    private Picker createPickerTree(List<OrganisationUnit> units, List<Program> programs) {
        Map<String, OrganisationUnit> organisationUnitMap = ModelUtils.toMap(units);
        Map<String, Program> assignedProgramsMap = ModelUtils.toMap(programs);

        String chooseOrganisationUnit = selectorView != null ? selectorView
                .getPickerLabel(SelectorView.ID_CHOOSE_ORGANISATION_UNIT) : "";
        String chooseProgram = selectorView != null ? selectorView
                .getPickerLabel(SelectorView.ID_CHOOSE_PROGRAM) : "";

        if (selectorView != null &&
                (organisationUnitMap == null || organisationUnitMap.isEmpty())) {
            selectorView.showNoOrganisationUnitsError();
        }

        Picker rootPicker = new Picker.Builder()
                .hint(chooseOrganisationUnit)
                .build();
        for (String unitKey : organisationUnitMap.keySet()) {
            // creating organisation unit picker items
            OrganisationUnit organisationUnit = organisationUnitMap.get(unitKey);
            Picker organisationUnitPicker = new Picker.Builder()
                    .id(organisationUnit.getUid())
                    .name(organisationUnit.getDisplayName())
                    .hint(chooseProgram)
                    .parent(rootPicker)
                    .build();

            if (organisationUnit.getPrograms() != null && !organisationUnit.getPrograms().isEmpty()) {
                for (Program program : organisationUnit.getPrograms()) {
                    Program assignedProgram = assignedProgramsMap.get(program.getUid());

                    if (assignedProgram != null && ProgramType.WITHOUT_REGISTRATION
                            .equals(assignedProgram.getProgramType())) {
                        Picker programPicker = new Picker.Builder()
                                .id(assignedProgram.getUid())
                                .name(assignedProgram.getDisplayName())
                                .parent(organisationUnitPicker)
                                .build();
                        organisationUnitPicker.addChild(programPicker);
                    }
                }
            }
            rootPicker.addChild(organisationUnitPicker);
        }

        // set saved selections or default ones:
        if (sessionPreferences.getSelectedPickerUid(0) != null) {
            traverseAndSetSavedSelection(rootPicker);
        } else {
            // Traverse the tree. If there is a path with nodes
            // which have only one child, set default selection
            traverseAndSetDefaultSelection(rootPicker);
        }
        return rootPicker;
    }

    private void traverseAndSetSavedSelection(Picker node) {
        int treeLevel = 0;
        while (node != null) {
            String pickerId = sessionPreferences.getSelectedPickerUid(treeLevel);
            if (pickerId != null) {
                for (Picker child : node.getChildren()) {

                    if (child.getId().equals(pickerId)) {
                        node.setSelectedChild(child);
                        break;
                    }
                }
            }
            treeLevel++;
            node = node.getSelectedChild();
        }
    }

    private Observable<List<OrganisationUnit>> getOrganisationUnits() {
        return Observable.just(organisationUnitInteractor.store().queryAll());
    }

    private Observable<List<Program>> getPrograms() {
        return Observable.just(programInteractor.store().queryAll());
    }

    private Observable<Program> getProgram(String uid) {
        return Observable.just(programInteractor.store().queryByUid(uid));
    }

    private Observable<List<Event>> listEventsByOrgUnitProgram(OrganisationUnit organisationUnit, Program program) {
        return Observable.just(eventInteractor.store().query(organisationUnit.getUid(), program.getUid()));
    }

    private Observable<Event> getEvent(String uid) {
        return Observable.just(eventInteractor.store().queryByUid(uid));
    }
}
