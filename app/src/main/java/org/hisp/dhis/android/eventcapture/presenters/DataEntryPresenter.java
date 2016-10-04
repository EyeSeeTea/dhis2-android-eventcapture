package org.hisp.dhis.android.eventcapture.presenters;

import org.hisp.dhis.client.sdk.ui.bindings.presenters.Presenter;

public interface DataEntryPresenter extends Presenter {
    void createDataEntryFormStage(String eventid, String programId, String programStageId);

    void createDataEntryFormSection(String eventId, String programId, String programStageId, String programStageSectionId);
}
