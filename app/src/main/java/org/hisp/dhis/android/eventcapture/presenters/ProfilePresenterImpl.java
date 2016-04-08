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

import android.support.v4.util.Pair;

import org.hisp.dhis.android.eventcapture.views.fragments.ProfileView;
import org.hisp.dhis.client.sdk.android.api.D2;
import org.hisp.dhis.client.sdk.models.user.UserAccount;
import org.hisp.dhis.client.sdk.ui.models.DataEntityText;
import org.hisp.dhis.client.sdk.ui.models.DataEntity.Type;
import org.hisp.dhis.client.sdk.ui.models.DataEntity;
import org.hisp.dhis.client.sdk.ui.models.OnValueChangeListener;

import java.util.ArrayList;
import java.util.List;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import timber.log.Timber;

public class ProfilePresenterImpl implements ProfilePresenter {
    private ProfileView profileView;
    private Subscription profileSubscription;
    private Subscription saveProfileSubscription;

    public ProfilePresenterImpl(ProfileView profileView) {
        this.profileView = profileView;
    }

    @Override
    public void listUserAccountFields() {
        profileSubscription = D2.me().account()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .map(new Func1<UserAccount, List<DataEntity>>() {
                    @Override
                    public List<DataEntity> call(UserAccount userAccount) {
                        return transformUserAccount(userAccount);
                    }
                })
                .subscribe(new Action1<List<DataEntity>>() {
                    @Override
                    public void call(List<DataEntity> dataEntities) {
                        if (profileView != null) {
                            profileView.setProfileFields(dataEntities);
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        Timber.e(throwable, "error reading user account details");
                    }
                });
    }
//
//    public void onDestroy() {
//        if (profileSubscription != null && !profileSubscription.isUnsubscribed()) {
//            profileSubscription.isUnsubscribed();
//        }
//        if (saveProfileSubscription != null && !saveProfileSubscription.isUnsubscribed()) {
//            saveProfileSubscription.unsubscribe();
//        }
//
//        profileView = null;
//        profileSubscription = null;
//        saveProfileSubscription = null;
//    }


    private List<DataEntity> transformUserAccount(UserAccount account) {
        List<DataEntity> dataEntities = new ArrayList<>();
        RxProfileValueChangedListener onProfileValueChangedListener = new RxProfileValueChangedListener();
        onProfileValueChangedListener.setUserAccount(account);

        dataEntities.add(DataEntityText.create("First name", account.getFirstName(), Type.TEXT,
                onProfileValueChangedListener));
        dataEntities.add(DataEntityText.create("Surname", account.getSurname(), Type.TEXT,
                onProfileValueChangedListener));
        dataEntities.add(DataEntityText.create("Gender", account.getGender(), Type.AUTO_COMPLETE,
                onProfileValueChangedListener));
        dataEntities.add(DataEntityText.create("Birthday", account.getBirthday(), Type.DATE,
                onProfileValueChangedListener));
        dataEntities.add(DataEntityText.create("Introduction", account.getIntroduction(), Type
                .TRUE_ONLY, onProfileValueChangedListener));
        dataEntities.add(DataEntityText.create("Education", account.getEducation(), Type.BOOLEAN,
                onProfileValueChangedListener));
        dataEntities.add(DataEntityText.create("Employer", account.getEmployer(), Type.TEXT,
                onProfileValueChangedListener));
        dataEntities.add(DataEntityText.create("Interests", account.getInterests(),
                Type.TEXT, onProfileValueChangedListener));
        dataEntities.add(DataEntityText.create("Job title", account.getJobTitle(), Type.TEXT,
                onProfileValueChangedListener));
        dataEntities.add(DataEntityText.create("Languages", account.getLanguages(), Type.TEXT,
                onProfileValueChangedListener));
        dataEntities.add(DataEntityText.create("Email", account.getEmail(), Type.TEXT,
                onProfileValueChangedListener));
        dataEntities.add(DataEntityText.create("Phone number", account.getPhoneNumber(), Type.INTEGER,
                onProfileValueChangedListener));

        return dataEntities;
    }


    private class RxProfileValueChangedListener implements OnValueChangeListener<Pair<CharSequence, CharSequence>> {
        private UserAccount userAccount;

        @Override
        public void onValueChanged(Pair<CharSequence, CharSequence> keyValue) {
            if ("First name".equals(keyValue.first)) {
                userAccount.setFirstName(keyValue.second.toString());
            } else if ("Surname".equals(keyValue.first)) {
                userAccount.setSurname(keyValue.second.toString());
            } else if ("Gender".equals(keyValue.first)) {
                userAccount.setGender(keyValue.second.toString());
            } else if ("Birthday".equals(keyValue.first)) {
                userAccount.setBirthday(keyValue.second.toString());
            } else if ("Introduction".equals(keyValue.first)) {
                userAccount.setIntroduction(keyValue.second.toString());
            } else if ("Education".equals(keyValue.first)) {
                userAccount.setEducation(keyValue.second.toString());
            } else if ("Employer".equals(keyValue.first)) {
                userAccount.setEmployer(keyValue.second.toString());
            } else if ("Interests".equals(keyValue.first)) {
                userAccount.setInterests(keyValue.second.toString());
            } else if ("Job title".equals(keyValue.first)) {
                userAccount.setJobTitle(keyValue.second.toString());
            } else if ("Languages".equals(keyValue.first)) {
                userAccount.setLanguages(keyValue.second.toString());
            } else if ("Email".equals(keyValue.first)) {
                userAccount.setEmail(keyValue.second.toString());
            } else if ("Phone number".equals(keyValue.first)) {
                userAccount.setPhoneNumber(keyValue.second.toString());
            } else {

                throw new UnsupportedOperationException("Unsupported key");
            }

            saveProfileSubscription = D2.me().save(userAccount)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeOn(Schedulers.io()).subscribe(
                            new Action1<Boolean>() {
                                @Override
                                public void call(Boolean isSaved) {
                                    Timber.d("userAccount successfully saved");
                                }
                            }
                            , new Action1<Throwable>() {
                                @Override
                                public void call(Throwable throwable) {
                                    Timber.d("userAccount has failed saving");
                                }
                            });
        }

        public void setUserAccount(UserAccount userAccount) {
            this.userAccount = userAccount;
        }
    }
}
