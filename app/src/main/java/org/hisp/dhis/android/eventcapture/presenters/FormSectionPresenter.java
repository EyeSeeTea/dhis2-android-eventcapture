package org.hisp.dhis.android.eventcapture.presenters;

import org.hisp.dhis.client.sdk.models.event.Event;
import org.hisp.dhis.client.sdk.ui.bindings.presenters.Presenter;

import java.util.Date;

public interface FormSectionPresenter extends Presenter {
    void createDataEntryForm(String eventUid);

    void saveEventDate(String eventUid, Date eventDate);

    void saveEventStatus(String eventUid, Event.EventStatus eventStatus);

    void subscribeToLocations();

    void stopLocationUpdates();
}
