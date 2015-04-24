package org.hisp.dhis2.android.eventcapture.events;

import org.hisp.dhis2.android.eventcapture.adapters.rows.events.EventItemStatus;
import org.hisp.dhis2.android.sdk.persistence.models.Event;

/**
 * Created by araz on 24.04.2015.
 */
public class OnEventClick {
    private final Event event;
    private final EventItemStatus status;
    private final boolean onDescriptionClick;

    public OnEventClick(Event event, EventItemStatus status,
                        boolean description) {
        this.event = event;
        this.status = status;
        this.onDescriptionClick = description;
    }

    public Event getEvent() {
        return event;
    }

    public boolean isOnDescriptionClick() {
        return onDescriptionClick;
    }

    public EventItemStatus getStatus() {
        return status;
    }
}
