package org.hisp.dhis.android.eventcapture.views;

import android.location.Location;

import org.hisp.dhis.client.sdk.models.event.Event;
import org.hisp.dhis.client.sdk.ui.bindings.views.View;
import org.hisp.dhis.client.sdk.ui.models.FormSection;
import org.hisp.dhis.client.sdk.ui.models.Picker;

import java.util.List;

public interface FormSectionView extends View {

    /**
     * Should be called in cases when ProgramStage
     * does not contain any explicit sections
     */
    void showFormDefaultSection(String formSectionId);

    /**
     * Tells view to render form sections
     * @param formSections List of FormSections
     */
    void showFormSections(List<FormSection> formSections);

    void setFormSectionsPicker(Picker picker);

    void showReportDatePicker(String hint, String value);

    void showCoordinatesPicker(String latitude, String longitude);

    void showEventStatus(Event.EventStatus eventStatus);

    void setLocation(Location location);

    void setLocationButtonState(boolean enabled);
}
