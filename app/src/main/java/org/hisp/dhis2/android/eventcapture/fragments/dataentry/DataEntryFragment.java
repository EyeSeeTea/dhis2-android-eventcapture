/*
 * Copyright (c) 2015, dhis2
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.hisp.dhis2.android.eventcapture.fragments.dataentry;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.raizlabs.android.dbflow.structure.Model;
import com.squareup.otto.Subscribe;

import org.hisp.dhis2.android.eventcapture.EventCaptureApplication;
import org.hisp.dhis2.android.eventcapture.INavigationHandler;
import org.hisp.dhis2.android.eventcapture.MainActivity;
import org.hisp.dhis2.android.eventcapture.OnBackPressedListener;
import org.hisp.dhis2.android.eventcapture.R;
import org.hisp.dhis2.android.eventcapture.adapters.DataValueAdapter;
import org.hisp.dhis2.android.eventcapture.adapters.SectionAdapter;
import org.hisp.dhis2.android.eventcapture.adapters.rows.AbsTextWatcher;
import org.hisp.dhis2.android.eventcapture.adapters.rows.dataentry.IndicatorRow;
import org.hisp.dhis2.android.eventcapture.events.EditTextValueChangedEvent;
import org.hisp.dhis2.android.eventcapture.loaders.DbLoader;
import org.hisp.dhis2.android.sdk.controllers.Dhis2;
import org.hisp.dhis2.android.sdk.persistence.models.DataValue;
import org.hisp.dhis2.android.sdk.persistence.models.ProgramRule;
import org.hisp.dhis2.android.sdk.persistence.models.ProgramRuleAction;
import org.hisp.dhis2.android.sdk.persistence.models.ProgramStageDataElement;
import org.hisp.dhis2.android.sdk.utils.Utils;
import org.hisp.dhis2.android.sdk.utils.services.ProgramIndicatorService;
import org.hisp.dhis2.android.sdk.utils.services.ProgramRuleService;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static org.apache.commons.lang3.StringUtils.isEmpty;

public class DataEntryFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<DataEntryFragmentForm>,
        OnBackPressedListener, AdapterView.OnItemSelectedListener {
    public static final String TAG = DataEntryFragment.class.getSimpleName();

    private static final String EMPTY_FIELD = "";
    private static final String DATE_FORMAT = "YYYY-MM-dd";

    private static final int LOADER_ID = 1;
    private static final int INITIAL_POSITION = 0;

    private static final String EXTRA_ARGUMENTS = "extra:Arguments";
    private static final String EXTRA_SAVED_INSTANCE_STATE = "extra:savedInstanceState";

    private static final String ORG_UNIT_ID = "extra:orgUnitId";
    private static final String PROGRAM_ID = "extra:ProgramId";
    private static final String EVENT_ID = "extra:EventId";

    private ListView mListView;
    private ProgressBar mProgressBar;

    private View mSpinnerContainer;
    private Spinner mSpinner;

    private EditText mLatitude;
    private EditText mLongitude;
    private ImageButton mCaptureCoords;

    private SectionAdapter mSpinnerAdapter;
    private DataValueAdapter mListViewAdapter;

    private INavigationHandler mNavigationHandler;
    private DataEntryFragmentForm mForm;

    private ProgramRuleHelper mProgramRuleHelper;

    private View mReportDatePicker;
    private View mCoordinatePickerView;

    private boolean refreshing = false;
    private boolean hasDataChanged = false;

    public static DataEntryFragment newInstance(String unitId, String programId) {
        DataEntryFragment fragment = new DataEntryFragment();
        Bundle args = new Bundle();
        args.putString(ORG_UNIT_ID, unitId);
        args.putString(PROGRAM_ID, programId);
        fragment.setArguments(args);
        return fragment;
    }

    public static DataEntryFragment newInstance(String unitId, String programId,
                                                long eventId) {
        DataEntryFragment fragment = new DataEntryFragment();
        Bundle args = new Bundle();
        args.putString(ORG_UNIT_ID, unitId);
        args.putString(PROGRAM_ID, programId);
        args.putLong(EVENT_ID, eventId);
        fragment.setArguments(args);
        return fragment;
    }

    private static Map<String, ProgramStageDataElement> toMap(List<ProgramStageDataElement> dataElements) {
        Map<String, ProgramStageDataElement> dataElementMap = new HashMap<>();
        if (dataElements != null && !dataElements.isEmpty()) {
            for (ProgramStageDataElement dataElement : dataElements) {
                dataElementMap.put(dataElement.dataElement, dataElement);
            }
        }
        return dataElementMap;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        if (activity instanceof AppCompatActivity) {
            getActionBar().setDisplayShowTitleEnabled(false);
            getActionBar().setDisplayHomeAsUpEnabled(true);
            getActionBar().setHomeButtonEnabled(true);
        }

        if (activity instanceof MainActivity) {
            ((MainActivity) activity).setBackPressedListener(this);
        }

        if (activity instanceof INavigationHandler) {
            mNavigationHandler = (INavigationHandler) activity;
        } else {
            throw new IllegalArgumentException("Activity must implement INavigationHandler interface");
        }
    }

    @Override
    public void onDetach() {
        if (getActivity() != null &&
                getActivity() instanceof AppCompatActivity) {
            getActionBar().setDisplayShowTitleEnabled(true);
            getActionBar().setDisplayHomeAsUpEnabled(false);
            getActionBar().setHomeButtonEnabled(false);
        }

        // we need to nullify reference
        // to parent activity in order not to leak it
        if (getActivity() != null &&
                getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setBackPressedListener(null);
        }

        Dhis2.disableGps();
        mNavigationHandler = null;
        super.onDetach();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        EventCaptureApplication.getEventBus().register(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        EventCaptureApplication.getEventBus().unregister(this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_data_entry, menu);
        Log.d(TAG, "onCreateOptionsMenu");
        MenuItem menuItem = menu.findItem(R.id.action_new_event);
        if(!hasDataChanged) {
            menuItem.setEnabled(false);
            menuItem.getIcon().setAlpha(0x30);
        } else {
            menuItem.setEnabled(true);
            menuItem.getIcon().setAlpha(0xFF);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_data_entry, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mProgressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        mProgressBar.setVisibility(View.GONE);

        mListViewAdapter = new DataValueAdapter(getChildFragmentManager(),
                getLayoutInflater(savedInstanceState));
        mListView = (ListView) view.findViewById(R.id.datavalues_listview);
        mListView.setVisibility(View.VISIBLE);

        mReportDatePicker = LayoutInflater.from(getActivity())
                .inflate(R.layout.fragment_data_entry_date_picker, mListView, false);
        mListView.addHeaderView(mReportDatePicker);

        mCoordinatePickerView = LayoutInflater.from(getActivity())
                .inflate(R.layout.fragment_data_entry_coordinate_picker, mListView, false);
        mListView.addHeaderView(mCoordinatePickerView);
        mListView.setAdapter(mListViewAdapter);
    }

    @Override
    public void onDestroyView() {
        detachSpinner();
        super.onDestroyView();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            doBack();
            return true;
        } else if (menuItem.getItemId() == R.id.action_new_event) {
            submitEvent();
        }

        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Bundle argumentsBundle = new Bundle();
        argumentsBundle.putBundle(EXTRA_ARGUMENTS, getArguments());
        argumentsBundle.putBundle(EXTRA_SAVED_INSTANCE_STATE, savedInstanceState);
        getLoaderManager().initLoader(LOADER_ID, argumentsBundle, this);

        mProgressBar.setVisibility(View.VISIBLE);
        mListView.setVisibility(View.GONE);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    long timerStart = -1;

    @Override
    public Loader<DataEntryFragmentForm> onCreateLoader(int id, Bundle args) {
        if (LOADER_ID == id && isAdded()) {
            // Adding Tables for tracking here is dangerous (since MetaData updates in background
            // can trigger reload of values from db which will reset all fields).
            // Hence, it would be more safe not to track any changes in any tables
            timerStart = System.currentTimeMillis();
            List<Class<? extends Model>> modelsToTrack = new ArrayList<>();
            Bundle fragmentArguments = args.getBundle(EXTRA_ARGUMENTS);
            return new DbLoader<>(
                    getActivity().getBaseContext(), modelsToTrack, new DataEntryFragmentQuery(
                    fragmentArguments.getString(ORG_UNIT_ID, null),
                    fragmentArguments.getString(PROGRAM_ID, null),
                    fragmentArguments.getLong(EVENT_ID, -1)
            )
            );
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<DataEntryFragmentForm> loader, DataEntryFragmentForm data) {
        if (loader.getId() == LOADER_ID && isAdded()) {
            mProgressBar.setVisibility(View.GONE);
            mListView.setVisibility(View.VISIBLE);

            System.out.println("TIME: " + (System.currentTimeMillis() - timerStart));
            mForm = data;
            mProgramRuleHelper = new ProgramRuleHelper(mForm.getStage().getProgram());

            if (data.getStage() != null) {
                attachDatePicker();
            }

            if (data.getStage() != null &&
                    data.getStage().captureCoordinates) {
                attachCoordinatePicker();
            } else {
                if (mCoordinatePickerView != null) {
                    mCoordinatePickerView.setVisibility(View.GONE);
                    mCoordinatePickerView.setLayoutParams(new AbsListView.
                            LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, 1));
                }
            }

            if (!data.getSections().isEmpty()) {
                if (data.getSections().size() > 1) {
                    attachSpinner();
                    mSpinnerAdapter.swapData(data.getSections());
                } else {
                    DataEntryFragmentSection section = data.getSections().get(0);
                    mListViewAdapter.swapData(section.getRows());
                    evaluateRules();
                }
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<DataEntryFragmentForm> loader) {
        if (loader.getId() == LOADER_ID) {
            if (mSpinnerAdapter != null) {
                mSpinnerAdapter.swapData(null);
            }
            if (mListViewAdapter != null) {
                mListViewAdapter.swapData(null);
            }
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        DataEntryFragmentSection section = (DataEntryFragmentSection)
                mSpinnerAdapter.getItem(position);

        if (section != null) {
            mListView.smoothScrollToPosition(INITIAL_POSITION);
            mListViewAdapter.swapData(section.getRows());
            evaluateRules();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // stub implementation
    }

    @Override
    public void doBack() {
        if (haveValuesChanged()) {
            Dhis2.getInstance().showConfirmDialog(getActivity(),
                    getString(R.string.discard), getString(R.string.discard_confirm_changes),
                    getString(R.string.discard),
                    getString(R.string.save_and_close),
                    getString(R.string.cancel),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            getFragmentManager().popBackStack();
                        }
                    }, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if(submitEvent()) {
                                getFragmentManager().popBackStack();
                            }
                        }
                    }, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
        } else {
            getFragmentManager().popBackStack();
        }
    }

    private boolean haveValuesChanged() {
        return hasDataChanged;
    }

    public void showWarningHiddenValuesDialog(ArrayList<String> affectedValues) {
        ValidationErrorDialog dialog = ValidationErrorDialog
                .newInstance(getString(R.string.warning_hidefieldwithvalue), affectedValues);
        dialog.show(getChildFragmentManager());
    }

    /**
     * Evaluates the ProgramRules for the current program and the current data values and applies
     * the results. This is for example used for hiding views if a rule contains skip logic
     */
    public void evaluateRules() {
        List<ProgramRule> rules = mForm.getStage().getProgram().getProgramRules();
        mListViewAdapter.resetHiding();
        ArrayList<String> affectedFieldsWithValue = new ArrayList<>();
        for (ProgramRule programRule : rules) {
            boolean actionTrue = ProgramRuleService.evaluate(programRule.condition, mForm.getEvent());
            for (ProgramRuleAction programRuleAction : programRule.getProgramRuleActions()) {
                boolean valueInField = applyProgramRuleAction(programRuleAction, actionTrue);
                if(valueInField) {
                    affectedFieldsWithValue.add(programRuleAction.dataElement);
                }
            }
        }
        if(!affectedFieldsWithValue.isEmpty()) {
            showWarningHiddenValuesDialog(affectedFieldsWithValue);
        }
        //todo dias make counter of stuff to show in dialog in rows with values are hidden.
    }

    public boolean applyProgramRuleAction(ProgramRuleAction programRuleAction, boolean actionTrue) {
        switch (programRuleAction.programRuleActionType) {
            case ProgramRuleAction.TYPE_HIDEFIELD:
                if (actionTrue) {
                    return hideField(programRuleAction.dataElement);
                }
                break;
        }
        return false;
    }

    /**
     * Hides a field in the listView of dataEntryRows. Returns true if the hidden field contained
     * a value
     * @param dataElement
     * @return
     */
    public boolean hideField(String dataElement) {
        mListViewAdapter.hideIndex(dataElement);
        refreshListView();
        DataValue dv = mForm.getDataValues().get(dataElement);
        if(dv!=null && dv.getValue()!=null && !dv.getValue().isEmpty()){
            return true;
        } else {
            return false;
        }
    }

    private void refreshListView() {
        Activity activity = getActivity();
        if (activity == null) {
            refreshing = false;
            return;
        }
        activity.runOnUiThread(new Thread() {
            public void run() {
                int start = mListView.getFirstVisiblePosition();
                int end = mListView.getLastVisiblePosition();
                for (int pos = 0; pos <= end - start; pos++) {
                    View view = mListView.getChildAt(pos);
                    if (view != null) {
                        int adapterPosition = view.getId();
                        if (adapterPosition < 0 || adapterPosition >= mListViewAdapter.getCount())
                            continue;
                        if (!view.hasFocus()) {
                            mListViewAdapter.getView(adapterPosition, view, mListView);
                        }
                    }
                }
                refreshing = false;
            }
        });
    }

    public void flagDataChanged(boolean changed) {
        if(hasDataChanged!=changed) {
            hasDataChanged = changed;
            getActivity().invalidateOptionsMenu();
        }
    }

    @Subscribe
    public void onRowValueChanged(final EditTextValueChangedEvent event) {
        flagDataChanged(true);
        if (mForm == null || mForm.getIndicatorRows() == null) {
            return;
        }
        if (refreshing)
            return; //we don't want to stack this up since it runs every time a character is entered for example
        refreshing = true;

        new Thread() {
            public void run() {
                /**
                 * Updating views based on ProgramRules
                 */
                if (event.isDataValue() && mProgramRuleHelper.dataElementInRule(event.getId())) {
                    evaluateRules();
                }

                /*
                * updating indicator values in rows
                * */
                for (IndicatorRow indicatorRow : mForm.getIndicatorRows()) {
                    String newValue = ProgramIndicatorService.
                            getProgramIndicatorValue(mForm.getEvent(), indicatorRow.getIndicator());
                    if (newValue == null) {
                        newValue = "";
                    }
                    if (!newValue.equals(indicatorRow.getValue())) {
                        indicatorRow.updateValue(newValue);
                    }
                }

                /*
                * Calling adapter's getView in order to render changes in visible IndicatorRows
                * */
                refreshListView();
            }
        }.start();
    }

    private ActionBar getActionBar() {
        if (getActivity() != null &&
                getActivity() instanceof AppCompatActivity) {
            return ((AppCompatActivity) getActivity()).getSupportActionBar();
        } else {
            throw new IllegalArgumentException("Fragment should be attached to ActionBarActivity");
        }
    }

    private Toolbar getActionBarToolbar() {
        if (isAdded() && getActivity() != null &&
                getActivity() instanceof MainActivity) {
            return (Toolbar) getActivity().findViewById(R.id.toolbar);
        } else {
            throw new IllegalArgumentException("Fragment should be attached to MainActivity");
        }
    }

    private void attachDatePicker() {
        if (mForm != null && isAdded()) {
            //final View mReportDatePicker = LayoutInflater.from(getActivity())
            //        .inflate(R.layout.fragment_data_entry_date_picker, mListView, false);
            final TextView label = (TextView) mReportDatePicker
                    .findViewById(R.id.text_label);
            final EditText datePickerEditText = (EditText) mReportDatePicker
                    .findViewById(R.id.date_picker_edit_text);
            final ImageButton clearDateButton = (ImageButton) mReportDatePicker
                    .findViewById(R.id.clear_edit_text);

            final DatePickerDialog.OnDateSetListener dateSetListener
                    = new DatePickerDialog.OnDateSetListener() {
                @Override public void onDateSet(DatePicker view, int year,
                                                int monthOfYear, int dayOfMonth) {
                    LocalDate date = new LocalDate(year, monthOfYear + 1, dayOfMonth);
                    String newValue = date.toString(DATE_FORMAT);
                    datePickerEditText.setText(newValue);
                    mForm.getEvent().setEventDate(newValue);
                }
            };
            clearDateButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    datePickerEditText.setText(EMPTY_FIELD);
                    mForm.getEvent().setEventDate(EMPTY_FIELD);
                }
            });
            datePickerEditText.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    LocalDate currentDate = new LocalDate();
                    DatePickerDialog picker = new DatePickerDialog(getActivity(),
                            dateSetListener, currentDate.getYear(),
                            currentDate.getMonthOfYear() - 1,
                            currentDate.getDayOfMonth());
                    picker.getDatePicker().setMaxDate(DateTime.now().getMillis());
                    picker.show();
                }
            });

            String reportDateDescription = mForm.getStage().reportDateDescription == null ?
                    getString(R.string.report_date) : mForm.getStage().reportDateDescription;
            label.setText(reportDateDescription);
            if (mForm.getEvent() != null && mForm.getEvent().getEventDate() != null) {
                DateTime date = DateTime.parse(mForm.getEvent().getEventDate());
                String newValue = date.toString(DATE_FORMAT);
                datePickerEditText.setText(newValue);
            }

            //mListView.addHeaderView(mReportDatePicker);
        }
    }

    private void attachCoordinatePicker() {
        if (mForm == null || mForm.getEvent() == null || !isAdded()) {
            return;
        }
        // Prepare GPS for work. Note, we should use base
        // context in order not to leak activity
        Dhis2.activateGps(getActivity().getBaseContext());

        Double latitude = mForm.getEvent().getLatitude();
        Double longitude = mForm.getEvent().getLongitude();

        LayoutInflater inflater = LayoutInflater.from(getActivity());

        mLatitude = (EditText) mCoordinatePickerView.findViewById(R.id.latitude_edittext);
        mLongitude = (EditText) mCoordinatePickerView.findViewById(R.id.longitude_edittext);
        mCaptureCoords = (ImageButton) mCoordinatePickerView.findViewById(R.id.capture_coordinates);

        if (latitude != null) {
            mLatitude.setText(String.valueOf(latitude));
        }

        if (longitude != null) {
            mLongitude.setText(String.valueOf(longitude));
        }

        final String latitudeMessage = getString(R.string.latitude_error_message);
        final String longitudeMessage = getString(R.string.longitude_error_message);

        mLatitude.addTextChangedListener(new AbsTextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > 1) {
                    double value = Double.parseDouble(s.toString());
                    if (value < -90 || value > 90) {
                        mLatitude.setError(latitudeMessage);
                    }
                    mForm.getEvent().setLatitude(Double.valueOf(value));
                }
            }
        });

        mLongitude.addTextChangedListener(new AbsTextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > 1) {
                    double value = Double.parseDouble(s.toString());
                    if (value < -180 || value > 180) {
                        mLongitude.setError(longitudeMessage);
                    }
                    mForm.getEvent().setLongitude(Double.valueOf(value));
                }
            }
        });

        mCaptureCoords.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Location location = Dhis2.getLocation(getActivity().getBaseContext());

                mLatitude.setText(String.valueOf(location.getLatitude()));
                mLongitude.setText(String.valueOf(location.getLongitude()));
            }
        });
    }

    private void attachSpinner() {
        if (!isSpinnerAttached()) {
            Toolbar toolbar = getActionBarToolbar();

            LayoutInflater inflater = LayoutInflater.from(getActivity());
            mSpinnerContainer = inflater.inflate(
                    R.layout.toolbar_spinner, toolbar, false);
            ImageView previousSectionButton = (ImageView) mSpinnerContainer
                    .findViewById(R.id.previous_section);
            ImageView nextSectionButton = (ImageView) mSpinnerContainer
                    .findViewById(R.id.next_section);
            ActionBar.LayoutParams lp = new ActionBar.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            toolbar.addView(mSpinnerContainer, lp);

            mSpinnerAdapter = new SectionAdapter(inflater);

            mSpinner = (Spinner) mSpinnerContainer.findViewById(R.id.toolbar_spinner);
            mSpinner.setAdapter(mSpinnerAdapter);
            mSpinner.setOnItemSelectedListener(this);

            previousSectionButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    int currentPosition = mSpinner.getSelectedItemPosition();
                    if (!(currentPosition - 1 < 0)) {
                        mSpinner.setSelection(currentPosition - 1);
                    }
                }
            });

            nextSectionButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    int currentPosition = mSpinner.getSelectedItemPosition();
                    if (!(currentPosition + 1 >= mSpinnerAdapter.getCount())) {
                        mSpinner.setSelection(currentPosition + 1);
                    }
                }
            });
        }
    }

    private void detachSpinner() {
        if (isSpinnerAttached()) {
            if (mSpinnerContainer != null) {
                ((ViewGroup) mSpinnerContainer.getParent()).removeView(mSpinnerContainer);
                mSpinnerContainer = null;
                mSpinner = null;
                if (mSpinnerAdapter != null) {
                    mSpinnerAdapter.swapData(null);
                    mSpinnerAdapter = null;
                }
            }
        }
    }

    private boolean isSpinnerAttached() {
        return mSpinnerContainer != null;
    }

    /**
     * returns true if the event was successfully saved
     * @return
     */
    private boolean submitEvent() {
        if (mForm != null && isAdded()) {
            ArrayList<String> errors = isEventValid();
            if (errors.isEmpty()) {
                mForm.getEvent().setFromServer(true);
                mForm.getEvent().setLastUpdated(Utils.getCurrentTime());
                mForm.getEvent().save(true);

                /*workaround for dbflow concurrency bug. This ensures that datavalues are saved
                before Dhis2 sends data to server to avoid some data values not being sent in race
                conditions*/
                mForm.getEvent().setFromServer(false);
                mForm.getEvent().save(true);
                final Context context = getActivity().getBaseContext();

                TimerTask timerTask = new TimerTask() {
                    @Override
                    public void run() {
                        Dhis2.sendLocalData(context);
                    }
                };
                Timer timer = new Timer();
                timer.schedule(timerTask, 5000);
                flagDataChanged(false);
                return true;
            } else {
                ValidationErrorDialog dialog = ValidationErrorDialog
                        .newInstance(errors);
                dialog.show(getChildFragmentManager());
                return false;
            }
        } else {
            return false;
        }
    }

    private ArrayList<String> isEventValid() {
        ArrayList<String> errors = new ArrayList<>();

        if (mForm == null || mForm.getEvent() == null || mForm.getStage() == null) {
            return errors;
        }

        if (isEmpty(mForm.getEvent().getEventDate())) {
            String reportDateDescription = mForm.getStage().reportDateDescription == null ?
                    getString(R.string.report_date) : mForm.getStage().reportDateDescription;
            errors.add(reportDateDescription);
        }

        Map<String, ProgramStageDataElement> dataElements = toMap(
                mForm.getStage().getProgramStageDataElements()
        );

        for (DataValue dataValue : mForm.getEvent().getDataValues()) {
            ProgramStageDataElement dataElement = dataElements.get(dataValue.dataElement);
            if (dataElement.compulsory && isEmpty(dataValue.getValue())) {
                errors.add(mForm.getDataElementNames().get(dataValue.dataElement));
            }
        }

        return errors;
    }
}