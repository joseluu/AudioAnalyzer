package com.audioanalyzer.calibration;

import com.audioanalyzer.audio.AudioEngine;
import com.audioanalyzer.audio.SignalGenerator;
import com.audioanalyzer.audio.SyncDetector;
import com.audioanalyzer.model.CalibrationResult;
import com.audioanalyzer.model.MeasurementPoint;

import java.util.HashMap;
import java.util.Map;

public class CalibrationManager {

    public interface CalibrationListener {
        void onDelayMeasured(int delaySamples, double delayMs);
        void onPhaseVerification(double frequency, double phaseDegrees);
        void onCalibrationComplete(CalibrationResult result);
        void onError(String message);
    }

    private static final double BURST_FREQUENCY = 1000.0;
    private static final int NUM_BURSTS = 5;
    private static final double[] VERIFICATION_FREQUENCIES = {30.0, 100.0, 300.0, 1000.0};

    private final int sampleRate;
    private final SignalGenerator generator;
    private final SyncDetector detector;
    private final DelayEstimator delayEstimator;

    private int measuredDelaySamples;
    private int fineAdjustment = 0;
    private final Map<Double, Double> verificationPhases = new HashMap<>();

    private AudioEngine audioEngine;
    private CalibrationListener listener;
    private volatile boolean calibrating = false;

    // Buffers for calibration
    private float[] recordBuffer;
    private int recordPosition;
    private int totalRecordSamples;

    // Current state
    private enum State { IDLE, MEASURING_DELAY, VERIFYING_PHASE }
    private volatile State state = State.IDLE;
    private int currentVerifyIndex;

    public CalibrationManager(int sampleRate) {
        this.sampleRate = sampleRate;
        this.generator = new SignalGenerator();
        this.detector = new SyncDetector();
        this.delayEstimator = new DelayEstimator();
    }

    public void setListener(CalibrationListener listener) {
        this.listener = listener;
    }

    public void setFineAdjustment(int adjustment) {
        this.fineAdjustment = adjustment;
    }

    public int getFineAdjustment() {
        return fineAdjustment;
    }

    public int getTotalDelay() {
        return measuredDelaySamples + fineAdjustment;
    }

    /**
     * Start delay measurement using burst cross-correlation.
     */
    public void startDelayMeasurement(AudioEngine engine) {
        if (calibrating) return;
        this.audioEngine = engine;
        calibrating = true;
        state = State.MEASURING_DELAY;

        // Burst parameters: 10ms signal, 500ms silence per burst, 5 bursts
        int signalSamples = (int) (0.010 * sampleRate);
        int silenceSamples = (int) (0.500 * sampleRate);
        int burstSpacing = signalSamples + silenceSamples;
        totalRecordSamples = burstSpacing * NUM_BURSTS + sampleRate; // + 1s buffer
        recordBuffer = new float[totalRecordSamples];
        recordPosition = 0;

        // Generate the burst sequence
        float[] burstSequence = new float[burstSpacing * NUM_BURSTS];
        for (int b = 0; b < NUM_BURSTS; b++) {
            float[] burst = new float[burstSpacing];
            generator.generateBurst(burst, BURST_FREQUENCY, sampleRate, 0, 0.8f,
                    signalSamples, silenceSamples);
            System.arraycopy(burst, 0, burstSequence, b * burstSpacing, burstSpacing);
        }

        // Play and record
        final float[] playData = burstSequence;
        final int[] playPos = {0};

        audioEngine.setCallback(new AudioEngine.AudioCallback() {
            @Override
            public void onWriteBuffer(float[] monoBuffer, int numFrames) {
                for (int i = 0; i < numFrames; i++) {
                    if (playPos[0] < playData.length) {
                        monoBuffer[i] = playData[playPos[0]++];
                    } else {
                        monoBuffer[i] = 0f;
                    }
                }
            }

            @Override
            public void onReadBuffer(float[] monoBuffer, int numFrames) {
                if (state != State.MEASURING_DELAY) return;
                int toCopy = Math.min(numFrames, totalRecordSamples - recordPosition);
                if (toCopy > 0) {
                    System.arraycopy(monoBuffer, 0, recordBuffer, recordPosition, toCopy);
                    recordPosition += toCopy;
                }

                if (recordPosition >= totalRecordSamples) {
                    state = State.IDLE;
                    processDelayMeasurement(signalSamples, silenceSamples);
                }
            }
        });

        audioEngine.start();
    }

    private void processDelayMeasurement(int signalSamples, int silenceSamples) {
        // Stop audio
        audioEngine.stop();

        // Generate reference burst for correlation
        float[] refBurst = new float[signalSamples];
        generator.generateSine(refBurst, BURST_FREQUENCY, sampleRate, 0, 0.8f);

        int maxDelay = (int) (0.200 * sampleRate); // 200ms max
        int burstSpacing = signalSamples + silenceSamples;

        double delay = delayEstimator.estimateDelayMultiBurst(
                refBurst, recordBuffer, burstSpacing, NUM_BURSTS, maxDelay);

        measuredDelaySamples = (int) Math.round(delay);
        double delayMs = delay / sampleRate * 1000.0;

        calibrating = false;

        if (listener != null) {
            listener.onDelayMeasured(measuredDelaySamples, delayMs);
        }
    }

    /**
     * Measure phase at a single verification frequency.
     */
    public void measurePhaseAtFrequency(AudioEngine engine, double frequency) {
        if (calibrating) return;
        this.audioEngine = engine;
        calibrating = true;
        state = State.VERIFYING_PHASE;

        int totalDelay = getTotalDelay();
        int measureSamples = SyncDetector.getMeasurementSamples(frequency, sampleRate);
        int settlingSamples = SyncDetector.getSettlingSamples(sampleRate);
        int totalPlaySamples = settlingSamples + measureSamples + sampleRate / 10;

        totalRecordSamples = totalPlaySamples;
        recordBuffer = new float[totalRecordSamples];
        recordPosition = 0;

        final double freq = frequency;
        final double[] phase = {0};
        final int[] playPos = {0};

        audioEngine.setCallback(new AudioEngine.AudioCallback() {
            @Override
            public void onWriteBuffer(float[] monoBuffer, int numFrames) {
                double phaseInc = 2.0 * Math.PI * freq / sampleRate;
                for (int i = 0; i < numFrames; i++) {
                    monoBuffer[i] = (float) (0.8 * Math.sin(phase[0]));
                    phase[0] += phaseInc;
                    playPos[0]++;
                }
                phase[0] %= (2.0 * Math.PI);
            }

            @Override
            public void onReadBuffer(float[] monoBuffer, int numFrames) {
                if (state != State.VERIFYING_PHASE) return;
                int toCopy = Math.min(numFrames, totalRecordSamples - recordPosition);
                if (toCopy > 0) {
                    System.arraycopy(monoBuffer, 0, recordBuffer, recordPosition, toCopy);
                    recordPosition += toCopy;
                }

                if (recordPosition >= totalRecordSamples) {
                    state = State.IDLE;
                    processPhaseVerification(freq, settlingSamples, measureSamples, totalDelay);
                }
            }
        });

        audioEngine.start();
    }

    private void processPhaseVerification(double frequency, int settlingSamples,
                                           int measureSamples, int totalDelay) {
        audioEngine.stop();

        MeasurementPoint point = detector.detect(
                recordBuffer, settlingSamples, measureSamples,
                frequency, sampleRate, totalDelay);

        verificationPhases.put(frequency, point.phaseDegrees);
        calibrating = false;

        if (listener != null) {
            listener.onPhaseVerification(frequency, point.phaseDegrees);
        }
    }

    /**
     * Run all 4 verification frequencies sequentially.
     */
    public void runAllVerifications(AudioEngine engine) {
        currentVerifyIndex = 0;
        runNextVerification(engine);
    }

    private void runNextVerification(AudioEngine engine) {
        if (currentVerifyIndex >= VERIFICATION_FREQUENCIES.length) {
            // All done - build result
            CalibrationResult result = buildResult();
            if (listener != null) {
                listener.onCalibrationComplete(result);
            }
            return;
        }

        // Set a temporary listener wrapper that chains to the next
        CalibrationListener originalListener = this.listener;
        this.listener = new CalibrationListener() {
            @Override
            public void onDelayMeasured(int delaySamples, double delayMs) {
                if (originalListener != null) originalListener.onDelayMeasured(delaySamples, delayMs);
            }

            @Override
            public void onPhaseVerification(double frequency, double phaseDegrees) {
                if (originalListener != null) originalListener.onPhaseVerification(frequency, phaseDegrees);
                CalibrationManager.this.listener = originalListener;
                currentVerifyIndex++;
                // Small delay before next measurement
                new Thread(() -> {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    runNextVerification(engine);
                }).start();
            }

            @Override
            public void onCalibrationComplete(CalibrationResult result) {
                if (originalListener != null) originalListener.onCalibrationComplete(result);
            }

            @Override
            public void onError(String message) {
                if (originalListener != null) originalListener.onError(message);
            }
        };

        measurePhaseAtFrequency(engine, VERIFICATION_FREQUENCIES[currentVerifyIndex]);
    }

    public CalibrationResult buildResult() {
        return new CalibrationResult(
                measuredDelaySamples, fineAdjustment, sampleRate,
                new HashMap<>(verificationPhases));
    }

    public double[] getVerificationFrequencies() {
        return VERIFICATION_FREQUENCIES;
    }

    public boolean isCalibrating() {
        return calibrating;
    }
}
