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

package org.hisp.dhis2.android.eventcapture.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.widget.CardView;
import android.util.AttributeSet;

import org.hisp.dhis2.android.eventcapture.R;
import org.hisp.dhis2.android.sdk.utils.ui.views.FontTextView;

public class CardTextViewButton extends CardView {
    private FontTextView mTextView;
    private CharSequence mHint;

    public CardTextViewButton(Context context) {
        super(context);
        init(context);
    }

    public CardTextViewButton(Context context, AttributeSet attributes) {
        super(context, attributes);
        init(context);

        if (!isInEditMode()) {
            TypedArray attrs = context.obtainStyledAttributes(
                    attributes, R.styleable.ButtonHint);
            mHint = attrs.getString(R.styleable.ButtonHint_hint);
            setText(mHint);
            attrs.recycle();
        }
    }

    private void init(Context context) {
        int pxs = getResources().getDimensionPixelSize(
                R.dimen.card_text_view_margin);
        LayoutParams textViewParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        textViewParams.setMargins(pxs, pxs, pxs, pxs);

        mTextView = new FontTextView(context);
        mTextView.setClickable(true);
        mTextView.setId(getId());
        mTextView.setBackgroundResource(
                R.drawable.spinner_background_holo_light);
        mTextView.setFont(getContext().getString(R.string.regular_font_name));
        mTextView.setLayoutParams(textViewParams);

        addView(mTextView);
    }

    public void setText(CharSequence sequence) {
        if (mTextView != null && sequence != null) {
            mTextView.setText(sequence);
        }
    }

    public void setOnClickListener(OnClickListener listener) {
        mTextView.setOnClickListener(listener);
    }

    @Override
    public void setEnabled(boolean isEnabled) {
        super.setEnabled(isEnabled);
        mTextView.setEnabled(isEnabled);
        setText(mHint);
    }
}
