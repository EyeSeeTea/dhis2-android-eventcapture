package org.hisp.dhis.android.eventcapture.fragments.settings;

import android.content.Context;

/**
 *
 * Created by Vladislav Georgiev Alfredov on 1/18/16.
 */
public interface ISettingsPresenter {
    void logout(Context context);

    void synchronize(Context context);

    void setUpdateFrequency(Context context, int frequency);

    int getUpdateFrequency(Context context);
}
