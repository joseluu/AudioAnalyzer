package com.audioanalyzer.measurement;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.audioanalyzer.R;
import com.audioanalyzer.audio.AudioEngine;
import com.audioanalyzer.model.MeasurementPoint;
import com.audioanalyzer.ui.ChartView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MeasurementActivity extends AppCompatActivity {

    private AudioEngine audioEngine;
    private MeasurementManager measurementManager;
    private Handler uiHandler;

    private ChartView chartView;
    private Button btnStart, btnStop, btnExport;
    private ToggleButton toggleScale;
    private ProgressBar progressBar;
    private TextView tvProgress;

    private int delaySamples;
    private boolean leftChannel;
    private int sampleRate;
    private List<MeasurementPoint> results = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measurement);

        uiHandler = new Handler(Looper.getMainLooper());

        delaySamples = getIntent().getIntExtra("delaySamples", 0);
        leftChannel = getIntent().getBooleanExtra("leftChannel", true);

        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        sampleRate = AudioEngine.getNativeSampleRate(am);

        audioEngine = new AudioEngine(sampleRate);
        audioEngine.setLeftChannel(leftChannel);
        measurementManager = new MeasurementManager(sampleRate, delaySamples);

        bindViews();
        setupListeners();
    }

    private void bindViews() {
        chartView = findViewById(R.id.chartView);
        btnStart = findViewById(R.id.btnStartMeasurement);
        btnStop = findViewById(R.id.btnStopMeasurement);
        btnExport = findViewById(R.id.btnExport);
        toggleScale = findViewById(R.id.toggleScale);
        progressBar = findViewById(R.id.progressBar);
        tvProgress = findViewById(R.id.tvProgress);
    }

    private void setupListeners() {
        btnStop.setEnabled(false);
        btnExport.setEnabled(false);

        toggleScale.setOnCheckedChangeListener((btn, isChecked) -> {
            chartView.setLogScale(!isChecked); // off=log, on=linear
            chartView.invalidate();
        });

        measurementManager.setListener(new MeasurementManager.MeasurementListener() {
            @Override
            public void onProgress(int current, int total, double currentFrequency) {
                uiHandler.post(() -> {
                    progressBar.setMax(total);
                    progressBar.setProgress(current);
                    int percent = total > 0 ? (current * 100 / total) : 0;
                    tvProgress.setText(String.format(Locale.US,
                            "Measuring: %.0f Hz  [%d%%]", currentFrequency, percent));
                });
            }

            @Override
            public void onPointMeasured(MeasurementPoint point) {
                uiHandler.post(() -> {
                    results.add(point);
                    chartView.setData(results);
                    chartView.invalidate();
                });
            }

            @Override
            public void onSweepComplete(List<MeasurementPoint> allResults) {
                uiHandler.post(() -> {
                    results = new ArrayList<>(allResults);
                    chartView.setData(results);
                    chartView.invalidate();
                    btnStart.setEnabled(true);
                    btnStop.setEnabled(false);
                    btnExport.setEnabled(!results.isEmpty());
                    tvProgress.setText(String.format(Locale.US,
                            "Complete: %d points measured", results.size()));
                });
            }

            @Override
            public void onError(String message) {
                uiHandler.post(() -> tvProgress.setText("Error: " + message));
            }
        });

        btnStart.setOnClickListener(v -> {
            results.clear();
            chartView.setData(results);
            chartView.invalidate();

            double maxFreq = sampleRate / 2.0 - 100; // below Nyquist
            List<Double> frequencies = FrequencySweep.generateFrequencies(
                    Math.min(maxFreq, 15000.0));

            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            btnExport.setEnabled(false);

            measurementManager.startSweep(audioEngine, frequencies);
        });

        btnStop.setOnClickListener(v -> {
            measurementManager.cancel();
            btnStop.setEnabled(false);
        });

        btnExport.setOnClickListener(v -> exportCsv());
    }

    private void exportCsv() {
        if (results.isEmpty()) return;

        // Find reference amplitude at 1 kHz (closest point)
        double refAmplitude = 1.0;
        double refPhase = 0.0;
        double minDist = Double.MAX_VALUE;
        for (MeasurementPoint p : results) {
            double dist = Math.abs(p.frequency - 1000.0);
            if (dist < minDist) {
                minDist = dist;
                refAmplitude = p.amplitude;
                refPhase = p.phaseDegrees;
            }
        }

        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "AudioAnalyzer");
        dir.mkdirs();

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File file = new File(dir, "measurement_" + timestamp + ".csv");

        try (FileWriter writer = new FileWriter(file)) {
            writer.write("frequency_Hz;amplitude_lin;amplitude_dB;phase_deg\n");
            for (MeasurementPoint p : results) {
                double db = p.getAmplitudeDb(refAmplitude);
                double relPhase = p.getRelativePhase(refPhase);
                writer.write(String.format(Locale.US, "%.2f;%.6f;%.2f;%.2f\n",
                        p.frequency, p.amplitude, db, relPhase));
            }
            tvProgress.setText("Exported: " + file.getAbsolutePath());

            // Share intent
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Audio Analyzer Measurement");
            startActivity(Intent.createChooser(shareIntent, "Share CSV"));

        } catch (IOException e) {
            tvProgress.setText("Export error: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (measurementManager.isRunning()) {
            measurementManager.cancel();
        }
        if (audioEngine != null && audioEngine.isRunning()) {
            audioEngine.stop();
        }
    }
}
