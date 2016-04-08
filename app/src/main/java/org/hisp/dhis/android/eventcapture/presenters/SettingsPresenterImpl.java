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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import org.hisp.dhis.android.eventcapture.R;
import org.hisp.dhis.android.eventcapture.views.activities.LoginActivity;
import org.hisp.dhis.android.eventcapture.model.AppAccountManager;
import org.hisp.dhis.android.eventcapture.views.fragments.SettingsFragment;
import org.hisp.dhis.client.sdk.android.api.D2;

/**
 * This is the presenter, using MVP.
 * This class controls what is shown in the view. (AbsSettingsFragment).
 * <p>
 * Created by Vladislav Georgiev Alfredov on 1/15/16.
 */
public class SettingsPresenterImpl implements SettingsPresenter {
    public static final String CLASS_TAG = SettingsPresenterImpl.class.getSimpleName();
    public final static String UPDATE_FREQUENCY = "update_frequency";
    public final static String BACKGROUND_SYNC = "background_sync";
    public final static String CRASH_REPORTS = "crash_reports";
    public final static String PREFS_NAME = "DHIS2";

    //Default values:
    public static final int DEFAULT_UPDATE_FREQUENCY = 1440; //one hour
    public static final Boolean DEFAULT_BACKGROUND_SYNC = true;
    public static final Boolean DEFAULT_CRASH_REPORTS = true;

    SettingsFragment mSettingsFragment;

    public SettingsPresenterImpl(SettingsFragment callback) {
        mSettingsFragment = callback;
    }

    @Override
    public void logout(Context context) {
        // D2.signOut();
        D2.me().signOut();

        // ActivityUtils.changeDefaultActivity(context, true);
        context.startActivity(new Intent(mSettingsFragment.getActivity(), LoginActivity.class));

        //TODO: When loging out functionality works test the following:
        //log in with 1 user, log out and log in with another.
        //Now sync triggers twice, once for each account. But the app is only logged in with one.
        // Maybe we should remove the account before/during logging out ?
        //removeAccountExplicitly(Account account)
    }

    @Override
    public void synchronize(Context context) {
        //Log.d("SettingsPresenterImpl", "Synchronize clicked, synchronizing...");
        AppAccountManager.getInstance().syncNow();
    }

    @Override
    public void setUpdateFrequency(Context context, int frequency) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(UPDATE_FREQUENCY, frequency);
        editor.apply();
        //Log.e(CLASS_TAG, "updateFrequency: " + frequency);

        AppAccountManager.getInstance().setPeriodicSync((long) (frequency * 60));
    }

    @Override
    public int getUpdateFrequency(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int updateFrequency = sharedPreferences.getInt(UPDATE_FREQUENCY, DEFAULT_UPDATE_FREQUENCY);
        //Log.e(CLASS_TAG, "updateFrequency: " + updateFrequency);
        return updateFrequency;
    }

    @Override
    public void setBackgroundSynchronisation(Context context, Boolean enabled) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(BACKGROUND_SYNC, enabled);
        editor.apply();
        //Log.e(CLASS_TAG, "backgroundSync: " + enabled);

        if (enabled) {
            if (!ContentResolver.getMasterSyncAutomatically()) {
                //display a notification to the user to enable synchronization globally.
                mSettingsFragment.showMessage(
                        mSettingsFragment.getResources().getString(R.string.sys_sync_disabled_warning));
            }
            synchronize(context);
            AppAccountManager.getInstance().setPeriodicSync((long) getUpdateFrequency(context));
        } else {
            AppAccountManager.getInstance().removePeriodicSync();
        }
    }

    @Override
    public Boolean getBackgroundSynchronisation(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Boolean enabled = sharedPreferences.getBoolean(BACKGROUND_SYNC, DEFAULT_BACKGROUND_SYNC);
        //Log.e(CLASS_TAG, "getBackroundSync : " + enabled);
        return enabled;
    }

    @Override
    public Boolean getCrashReports(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Boolean enabled = sharedPreferences.getBoolean(CRASH_REPORTS, DEFAULT_CRASH_REPORTS);
        Log.e(CLASS_TAG, " crash reports : " + enabled);
        return enabled;
    }

    @Override
    public void setCrashReports(Context context, Boolean enabled) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(CRASH_REPORTS, enabled);
        editor.apply();
        Log.e(CLASS_TAG, " crash reports: " + enabled);
    }

    public void setSettingsFragment(SettingsFragment s) {
        mSettingsFragment = s;
    }
}
