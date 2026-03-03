package com.audioanalyzer.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

/**
 * Compound view: SeekBar for fine delay adjustment (-50 to +50 samples)
 * with a label showing the current value.
 */
public class DelayAdjustView extends LinearLayout {

    private SeekBar seekBar;
    private TextView label;
    private OnAdjustmentChangedListener listener;

    public interface OnAdjustmentChangedListener {
        void onAdjustmentChanged(int adjustment);
    }

    public DelayAdjustView(Context context) {
        super(context);
        init();
    }

    public DelayAdjustView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        int padding = 16;
        setPadding(padding, padding, padding, padding);

        label = new TextView(getContext());
        label.setText("Fine adjustment: +0 samples");
        label.setTextSize(14f);
        addView(label);

        seekBar = new SeekBar(getContext());
        seekBar.setMax(100); // -50 to +50
        seekBar.setProgress(50); // center
        addView(seekBar);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int adjustment = progress - 50;
                label.setText(String.format(Locale.US, "Fine adjustment: %+d samples", adjustment));
                if (listener != null) {
                    listener.onAdjustmentChanged(adjustment);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {}

            @Override
            public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    public void setOnAdjustmentChangedListener(OnAdjustmentChangedListener listener) {
        this.listener = listener;
    }

    public int getAdjustment() {
        return seekBar.getProgress() - 50;
    }

    public void setAdjustment(int adjustment) {
        seekBar.setProgress(adjustment + 50);
    }
}
