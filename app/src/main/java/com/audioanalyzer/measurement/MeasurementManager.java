package com.audioanalyzer.measurement;

import com.audioanalyzer.audio.AudioEngine;
import com.audioanalyzer.audio.SyncDetector;
import com.audioanalyzer.model.MeasurementPoint;

import java.util.ArrayList;
import java.util.List;

public class MeasurementManager {

    public interface MeasurementListener {
        void onProgress(int current, int total, double currentFrequency);
        void onPointMeasured(MeasurementPoint point);
        void onSweepComplete(List<MeasurementPoint> results);
        void onError(String message);
    }

    private final int sampleRate;
    private final int delaySamples;
    private final SyncDetector detector;

    private AudioEngine audioEngine;
    private MeasurementListener listener;
    private volatile boolean cancelled = false;
    private volatile boolean running = false;
    private Thread sweepThread;

    public MeasurementManager(int sampleRate, int delaySamples) {
        this.sampleRate = sampleRate;
        this.delaySamples = delaySamples;
        this.detector = new SyncDetector();
    }

    public void setListener(MeasurementListener listener) {
        this.listener = listener;
    }

    public void startSweep(AudioEngine engine, List<Double> frequencies) {
        if (running) return;
        this.audioEngine = engine;
        this.cancelled = false;
        this.running = true;

        sweepThread = new Thread(() -> runSweep(frequencies), "FrequencySweep");
        sweepThread.start();
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean isRunning() {
        return running;
    }

    private void runSweep(List<Double> frequencies) {
        List<MeasurementPoint> results = new ArrayList<>();
        int total = frequencies.size();

        for (int i = 0; i < total; i++) {
            if (cancelled) break;

            double freq = frequencies.get(i);

            if (listener != null) {
                listener.onProgress(i, total, freq);
            }

            try {
                MeasurementPoint point = measureFrequency(freq);
                results.add(point);

                if (listener != null) {
                    listener.onPointMeasured(point);
                }

                // 10ms silence gap between measurements
                Thread.sleep(10);

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                if (listener != null) {
                    listener.onError("Error at " + freq + " Hz: " + e.getMessage());
                }
            }
        }

        running = false;

        if (listener != null) {
            if (cancelled) {
                listener.onSweepComplete(results); // partial results
            } else {
                listener.onSweepComplete(results);
            }
        }
    }

    private MeasurementPoint measureFrequency(double frequency) throws InterruptedException {
        int settlingSamples = SyncDetector.getSettlingSamples(sampleRate);
        int measureSamples = SyncDetector.getMeasurementSamples(frequency, sampleRate);
        int totalSamples = settlingSamples + measureSamples + sampleRate / 20; // + 50ms margin

        // Record buffer
        float[] recordBuffer = new float[totalSamples];
        final int[] recordPos = {0};
        final boolean[] recordDone = {false};
        final Object lock = new Object();

        // Phase continuity for generation
        final double[] phase = {0};

        audioEngine.setCallback(new AudioEngine.AudioCallback() {
            @Override
            public void onWriteBuffer(float[] monoBuffer, int numFrames) {
                double phaseInc = 2.0 * Math.PI * frequency / sampleRate;
                for (int j = 0; j < numFrames; j++) {
                    monoBuffer[j] = (float) (0.8 * Math.sin(phase[0]));
                    phase[0] += phaseInc;
                }
                phase[0] %= (2.0 * Math.PI);
            }

            @Override
            public void onReadBuffer(float[] monoBuffer, int numFrames) {
                synchronized (lock) {
                    if (recordDone[0]) return;
                    int toCopy = Math.min(numFrames, totalSamples - recordPos[0]);
                    if (toCopy > 0) {
                        System.arraycopy(monoBuffer, 0, recordBuffer, recordPos[0], toCopy);
                        recordPos[0] += toCopy;
                    }
                    if (recordPos[0] >= totalSamples) {
                        recordDone[0] = true;
                        lock.notifyAll();
                    }
                }
            }
        });

        audioEngine.start();

        // Wait for recording to complete
        synchronized (lock) {
            while (!recordDone[0] && !cancelled) {
                lock.wait(100);
            }
        }

        audioEngine.stop();

        if (cancelled) {
            throw new InterruptedException("Cancelled");
        }

        // Apply lock-in detection on the measurement portion (after settling)
        return detector.detect(recordBuffer, settlingSamples, measureSamples,
                frequency, sampleRate, delaySamples);
    }
}
