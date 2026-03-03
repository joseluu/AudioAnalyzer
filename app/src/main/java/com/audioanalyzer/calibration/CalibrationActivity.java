package com.audioanalyzer.calibration;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.audioanalyzer.R;
import com.audioanalyzer.audio.AudioEngine;
import com.audioanalyzer.measurement.MeasurementActivity;
import com.audioanalyzer.model.CalibrationResult;

import java.util.Locale;

public class CalibrationActivity extends AppCompatActivity {

    private AudioEngine audioEngine;
    private CalibrationManager calibrationManager;
    private Handler uiHandler;

    private RadioGroup channelGroup;
    private Button btnStart;
    private TextView tvDelay;
    private TextView tvPhase30, tvPhase100, tvPhase300, tvPhase1000;
    private Button btnRemeasure30, btnRemeasure100, btnRemeasure300, btnRemeasure1000;
    private SeekBar seekFineAdjust;
    private TextView tvFineAdjust;
    private Button btnValidate;
    private View phaseLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration);

        uiHandler = new Handler(Looper.getMainLooper());

        boolean leftChannel = getIntent().getBooleanExtra("leftChannel", true);

        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        int sampleRate = AudioEngine.getNativeSampleRate(am);

        audioEngine = new AudioEngine(sampleRate);
        audioEngine.setLeftChannel(leftChannel);
        calibrationManager = new CalibrationManager(sampleRate);

        bindViews();
        setupListeners();

        // Set initial channel selection
        if (leftChannel) {
            ((RadioButton) findViewById(R.id.radioLeft)).setChecked(true);
        } else {
            ((RadioButton) findViewById(R.id.radioRight)).setChecked(true);
        }

        phaseLayout.setVisibility(View.GONE);
    }

    private void bindViews() {
        channelGroup = findViewById(R.id.channelGroup);
        btnStart = findViewById(R.id.btnStartCalibration);
        tvDelay = findViewById(R.id.tvDelay);
        tvPhase30 = findViewById(R.id.tvPhase30);
        tvPhase100 = findViewById(R.id.tvPhase100);
        tvPhase300 = findViewById(R.id.tvPhase300);
        tvPhase1000 = findViewById(R.id.tvPhase1000);
        btnRemeasure30 = findViewById(R.id.btnRemeasure30);
        btnRemeasure100 = findViewById(R.id.btnRemeasure100);
        btnRemeasure300 = findViewById(R.id.btnRemeasure300);
        btnRemeasure1000 = findViewById(R.id.btnRemeasure1000);
        seekFineAdjust = findViewById(R.id.seekFineAdjust);
        tvFineAdjust = findViewById(R.id.tvFineAdjust);
        btnValidate = findViewById(R.id.btnValidate);
        phaseLayout = findViewById(R.id.phaseLayout);
    }

    private void setupListeners() {
        channelGroup.setOnCheckedChangeListener((group, checkedId) -> {
            audioEngine.setLeftChannel(checkedId == R.id.radioLeft);
        });

        calibrationManager.setListener(new CalibrationManager.CalibrationListener() {
            @Override
            public void onDelayMeasured(int delaySamples, double delayMs) {
                uiHandler.post(() -> {
                    tvDelay.setText(String.format(Locale.US,
                            "Measured delay: %d samples (%.2f ms)", delaySamples, delayMs));
                    btnStart.setEnabled(true);
                    phaseLayout.setVisibility(View.VISIBLE);

                    // Auto-run phase verification
                    runAllVerifications();
                });
            }

            @Override
            public void onPhaseVerification(double frequency, double phaseDegrees) {
                uiHandler.post(() -> updatePhaseDisplay(frequency, phaseDegrees));
            }

            @Override
            public void onCalibrationComplete(CalibrationResult result) {
                uiHandler.post(() -> {
                    enableRemeasureButtons(true);
                    btnValidate.setEnabled(true);
                });
            }

            @Override
            public void onError(String message) {
                uiHandler.post(() -> tvDelay.setText("Error: " + message));
            }
        });

        btnStart.setOnClickListener(v -> {
            btnStart.setEnabled(false);
            tvDelay.setText("Measuring delay...");
            phaseLayout.setVisibility(View.GONE);
            calibrationManager.startDelayMeasurement(audioEngine);
        });

        btnRemeasure30.setOnClickListener(v -> remeasure(30.0));
        btnRemeasure100.setOnClickListener(v -> remeasure(100.0));
        btnRemeasure300.setOnClickListener(v -> remeasure(300.0));
        btnRemeasure1000.setOnClickListener(v -> remeasure(1000.0));

        // Fine adjustment: range -50 to +50 (SeekBar 0-100, center = 50)
        seekFineAdjust.setMax(100);
        seekFineAdjust.setProgress(50);
        seekFineAdjust.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int adjustment = progress - 50;
                calibrationManager.setFineAdjustment(adjustment);
                tvFineAdjust.setText(String.format(Locale.US, "%+d samples", adjustment));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnValidate.setOnClickListener(v -> {
            CalibrationResult result = calibrationManager.buildResult();
            Intent intent = new Intent(CalibrationActivity.this, MeasurementActivity.class);
            intent.putExtra("delaySamples", result.totalDelay);
            intent.putExtra("leftChannel", audioEngine.isLeftChannel());
            startActivity(intent);
        });
    }

    private void runAllVerifications() {
        enableRemeasureButtons(false);
        btnValidate.setEnabled(false);
        calibrationManager.runAllVerifications(audioEngine);
    }

    private void remeasure(double frequency) {
        enableRemeasureButtons(false);
        calibrationManager.measurePhaseAtFrequency(audioEngine, frequency);

        // Re-enable after measurement (listener will fire)
        CalibrationManager.CalibrationListener prevListener = null;
        calibrationManager.setListener(new CalibrationManager.CalibrationListener() {
            @Override
            public void onDelayMeasured(int d, double ms) {}

            @Override
            public void onPhaseVerification(double freq, double phase) {
                uiHandler.post(() -> {
                    updatePhaseDisplay(freq, phase);
                    enableRemeasureButtons(true);
                });
            }

            @Override
            public void onCalibrationComplete(CalibrationResult result) {}

            @Override
            public void onError(String message) {
                uiHandler.post(() -> enableRemeasureButtons(true));
            }
        });
    }

    private void updatePhaseDisplay(double frequency, double phaseDegrees) {
        String text = String.format(Locale.US, "%.1f°", phaseDegrees);
        if (frequency == 30.0) tvPhase30.setText(text);
        else if (frequency == 100.0) tvPhase100.setText(text);
        else if (frequency == 300.0) tvPhase300.setText(text);
        else if (frequency == 1000.0) tvPhase1000.setText(text);
    }

    private void enableRemeasureButtons(boolean enabled) {
        btnRemeasure30.setEnabled(enabled);
        btnRemeasure100.setEnabled(enabled);
        btnRemeasure300.setEnabled(enabled);
        btnRemeasure1000.setEnabled(enabled);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioEngine != null && audioEngine.isRunning()) {
            audioEngine.stop();
        }
    }
}
