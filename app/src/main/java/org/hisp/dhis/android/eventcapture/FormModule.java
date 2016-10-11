package org.hisp.dhis.android.eventcapture;

import org.hisp.dhis.android.eventcapture.model.RxRulesEngine;
import org.hisp.dhis.android.eventcapture.presenters.DataEntryPresenter;
import org.hisp.dhis.android.eventcapture.presenters.DataEntryPresenterImpl;
import org.hisp.dhis.android.eventcapture.presenters.FormSectionPresenter;
import org.hisp.dhis.android.eventcapture.presenters.FormSectionPresenterImpl;
import org.hisp.dhis.client.sdk.core.event.EventInteractor;
import org.hisp.dhis.client.sdk.core.option.OptionSetInteractor;
import org.hisp.dhis.client.sdk.core.program.ProgramInteractor;
import org.hisp.dhis.client.sdk.core.trackedentity.TrackedEntityDataValueInteractor;
import org.hisp.dhis.client.sdk.core.user.UserInteractor;
import org.hisp.dhis.client.sdk.utils.Logger;

import javax.annotation.Nullable;

import dagger.Module;
import dagger.Provides;

@Module
public class FormModule {

    public FormModule() {
        // explicit empty constructor
    }

    @Provides
    @PerActivity
    public RxRulesEngine providesRuleEngine(
            @Nullable UserInteractor currentUserInteractor,
            @Nullable ProgramInteractor programInteractor,
            @Nullable EventInteractor eventInteractor, Logger logger) {
        return new RxRulesEngine(
                currentUserInteractor,
                programInteractor,
                eventInteractor, logger);
    }

    @Provides
    @PerActivity
    public FormSectionPresenter providesFormSectionPresenter(
            @Nullable ProgramInteractor programInteractor,
            @Nullable EventInteractor eventInteractor,
             RxRulesEngine rxRulesEngine,
            LocationProvider locationProvider, Logger logger) {
        return new FormSectionPresenterImpl(programInteractor, eventInteractor,
                rxRulesEngine, locationProvider, logger);
    }

    @Provides
    public DataEntryPresenter providesDataEntryPresenter(
            @Nullable UserInteractor currentUserInteractor,
            @Nullable ProgramInteractor programInteractor,
            @Nullable OptionSetInteractor optionSetInteractor,
            @Nullable EventInteractor eventInteractor,
            @Nullable TrackedEntityDataValueInteractor dataValueInteractor,
            RxRulesEngine rxRulesEngine, Logger logger) {
        return new DataEntryPresenterImpl(currentUserInteractor,
                programInteractor, optionSetInteractor,
                eventInteractor, dataValueInteractor, rxRulesEngine, logger);
    }
}
