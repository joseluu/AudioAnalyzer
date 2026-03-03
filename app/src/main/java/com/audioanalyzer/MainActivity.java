package com.audioanalyzer;

import android.Manifest;
import android.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.audioanalyzer.calibration.CalibrationActivity;
import com.audioanalyzer.measurement.MeasurementActivity;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_AUDIO = 100;

    private RadioGroup channelGroup;
    private Button btnCalibrate;
    private Button btnDirectMeasure;
    private TextView tvStatus;

    private boolean leftChannel = true;
    private int calibratedDelay = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        channelGroup = findViewById(R.id.mainChannelGroup);
        btnCalibrate = findViewById(R.id.btnCalibrate);
        btnDirectMeasure = findViewById(R.id.btnDirectMeasure);
        tvStatus = findViewById(R.id.tvStatus);

        channelGroup.setOnCheckedChangeListener((group, checkedId) -> {
            leftChannel = (checkedId == R.id.mainRadioLeft);
        });

        btnCalibrate.setOnClickListener(v -> {
            if (checkAudioPermission()) {
                launchCalibration();
            }
        });

        btnDirectMeasure.setOnClickListener(v -> {
            if (checkAudioPermission()) {
                launchMeasurement();
            }
        });

        // Request permission on start
        checkAudioPermission();
    }

    private boolean checkAudioPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_AUDIO);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                tvStatus.setText("Microphone permission granted. Ready to calibrate.");
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Permission Required")
                        .setMessage("Microphone permission is required for audio measurement. " +
                                "Please grant it in Settings.")
                        .setPositiveButton("OK", null)
                        .show();
            }
        }
    }

    private void launchCalibration() {
        Intent intent = new Intent(this, CalibrationActivity.class);
        intent.putExtra("leftChannel", leftChannel);
        startActivityForResult(intent, 1);
    }

    private void launchMeasurement() {
        Intent intent = new Intent(this, MeasurementActivity.class);
        intent.putExtra("delaySamples", calibratedDelay);
        intent.putExtra("leftChannel", leftChannel);
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // When returning from calibration, the MeasurementActivity is launched
        // directly from CalibrationActivity. We just update status here.
        tvStatus.setText("Calibration complete. You can re-calibrate or measure.");
        btnDirectMeasure.setEnabled(true);
    }
}
