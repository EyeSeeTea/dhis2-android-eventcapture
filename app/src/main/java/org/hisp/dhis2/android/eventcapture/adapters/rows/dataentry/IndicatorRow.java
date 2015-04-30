package org.hisp.dhis2.android.eventcapture.adapters.rows.dataentry;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.hisp.dhis2.android.eventcapture.R;
import org.hisp.dhis2.android.sdk.persistence.models.BaseValue;
import org.hisp.dhis2.android.sdk.persistence.models.ProgramIndicator;

public final class IndicatorRow implements DataEntryRow {
    private static final String EMPTY_FIELD = "";

    private final ProgramIndicator mIndicator;
    private String mValue;

    private boolean hidden = false;

    public IndicatorRow(ProgramIndicator indicator, String value) {
        mIndicator = indicator;
        mValue = value;
    }

    @Override
    public View getView(LayoutInflater inflater, View convertView, ViewGroup container) {
        View view;
        IndicatorViewHolder holder;

        if (convertView != null && convertView.getTag() instanceof IndicatorViewHolder) {
            view = convertView;
            holder = (IndicatorViewHolder) view.getTag();
        } else {
            View root = inflater.inflate(
                    R.layout.listview_row_indicator, container, false);
            holder = new IndicatorViewHolder(
                    (TextView) root.findViewById(R.id.text_label),
                    (TextView) root.findViewById(R.id.indicator_row)
            );

            root.setTag(holder);
            view = root;
        }

        if (mIndicator.name != null) {
            holder.textLabel.setText(mIndicator.name);
        } else {
            holder.textLabel.setText(EMPTY_FIELD);
        }

        holder.textValue.setText(mValue);
        return view;
    }

    @Override
    public int getViewType() {
        return DataEntryRowTypes.INDICATOR.ordinal();
    }

    @Override
    public BaseValue getBaseValue() {
        return null;
    }

    public void updateValue(String value) {
        mValue = value;
    }

    public String getValue() {
        return mValue;
    }

    public ProgramIndicator getIndicator() {
        return mIndicator;
    }

    public static class IndicatorViewHolder {
        final TextView textLabel;
        final TextView textValue;

        public IndicatorViewHolder(TextView textLabel,
                                   TextView textValue) {
            this.textLabel = textLabel;
            this.textValue = textValue;
        }
    }

    @Override
    public boolean isHidden() {
        return hidden;
    }

    @Override
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
}
