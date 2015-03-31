/*
 * Copyright (c) 2014, Araz Abishov
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.hisp.dhis2.android.eventcapture.fragments;

import android.os.Parcel;
import android.os.Parcelable;

public class SelectProgramFragmentState implements Parcelable {
    public static final Creator<SelectProgramFragmentState> CREATOR
            = new Creator<SelectProgramFragmentState>() {

        public SelectProgramFragmentState createFromParcel(Parcel in) {
            return new SelectProgramFragmentState(in);
        }

        public SelectProgramFragmentState[] newArray(int size) {
            return new SelectProgramFragmentState[size];
        }
    };
    private static final String TAG = SelectProgramFragmentState.class.getName();
    private static final int DEFAULT_INDEX = -1;
    private boolean syncInProcess;

    private String orgUnitLabel;
    private String orgUnitId;

    private String programName;
    private String programId;

    public SelectProgramFragmentState() {
    }

    public SelectProgramFragmentState(SelectProgramFragmentState state) {
        if (state != null) {
            setSyncInProcess(state.isSyncInProcess());
            setOrgUnit(state.getOrgUnitId(), state.getOrgUnitLabel());
            setDataSet(state.getProgramId(), state.getProgramName());
        }
    }

    private SelectProgramFragmentState(Parcel in) {
        syncInProcess = in.readInt() == 1;

        orgUnitLabel = in.readString();
        orgUnitId = in.readString();

        programName = in.readString();
        programId = in.readString();
    }

    @Override
    public int describeContents() {
        return TAG.length();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(syncInProcess ? 1 : 0);

        parcel.writeString(orgUnitLabel);
        parcel.writeString(orgUnitId);

        parcel.writeString(programName);
        parcel.writeString(programId);
    }

    public boolean isSyncInProcess() {
        return syncInProcess;
    }

    public void setSyncInProcess(boolean syncInProcess) {
        this.syncInProcess = syncInProcess;
    }

    public void setOrgUnit(String orgUnitId, String orgUnitLabel) {
        this.orgUnitId = orgUnitId;
        this.orgUnitLabel = orgUnitLabel;
    }

    public void resetOrgUnit() {
        orgUnitId = null;
        orgUnitLabel = null;
    }

    public boolean isOrgUnitEmpty() {
        return (orgUnitId == null || orgUnitLabel == null);
    }

    public String getOrgUnitLabel() {
        return orgUnitLabel;
    }

    public String getOrgUnitId() {
        return orgUnitId;
    }

    public void setDataSet(String dataSetId, String dataSetLabel) {
        this.programId = dataSetId;
        this.programName = dataSetLabel;
    }

    public void resetDataSet() {
        programId = null;
        programName = null;
    }

    public boolean isDataSetEmpty() {
        return (programId == null || programName == null);
    }

    public String getProgramName() {
        return programName;
    }

    public String getProgramId() {
        return programId;
    }
}