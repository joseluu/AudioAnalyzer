package com.audioanalyzer.audio;

import com.audioanalyzer.model.MeasurementPoint;

public class SyncDetector {

    /**
     * Perform lock-in (synchronous quadrature) detection.
     *
     * @param samples      microphone samples
     * @param frequency    detection frequency (Hz)
     * @param sampleRate   sampling rate
     * @param delaySamples delay in samples from calibration
     * @return MeasurementPoint with amplitude and phase
     */
    public MeasurementPoint detect(float[] samples, double frequency,
                                    int sampleRate, double delaySamples) {
        return detect(samples, 0, samples.length, frequency, sampleRate, delaySamples);
    }

    /**
     * Perform lock-in detection on a sub-range of the samples array.
     */
    public MeasurementPoint detect(float[] samples, int offset, int length,
                                    double frequency, int sampleRate,
                                    double delaySamples) {
        double sumI = 0.0;
        double sumQ = 0.0;
        double phaseIncrement = 2.0 * Math.PI * frequency / sampleRate;

        for (int i = 0; i < length; i++) {
            int n = offset + i;
            if (n >= samples.length) break;

            // Reference phase compensated for delay
            double refPhase = phaseIncrement * (i - delaySamples);

            double sample = samples[n];
            sumI += sample * Math.cos(refPhase);
            sumQ += sample * Math.sin(refPhase);
        }

        // Normalize: factor of 2/N for lock-in
        double scale = 2.0 / length;
        double I = sumI * scale;
        double Q = sumQ * scale;

        double amplitude = Math.sqrt(I * I + Q * Q);
        double phaseRad = Math.atan2(Q, I);
        double phaseDeg = Math.toDegrees(phaseRad);

        return new MeasurementPoint(frequency, amplitude, phaseDeg);
    }

    /**
     * Calculate the measurement duration in samples.
     * Duration = max(100 cycles / f, 100ms)
     */
    public static int getMeasurementSamples(double frequency, int sampleRate) {
        double durationCycles = 100.0 / frequency; // seconds for 100 cycles
        double durationMin = 0.1; // 100ms minimum
        double duration = Math.max(durationCycles, durationMin);
        return (int) (duration * sampleRate);
    }

    /**
     * Calculate settling time in samples (20ms).
     */
    public static int getSettlingSamples(int sampleRate) {
        return (int) (0.020 * sampleRate);
    }
}
