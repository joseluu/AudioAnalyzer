package com.audioanalyzer.measurement;

import java.util.ArrayList;
import java.util.List;

public class FrequencySweep {

    /**
     * Generate the complete frequency list for measurement.
     *
     * Band 30-100 Hz: semitone steps (1/12 octave)
     * Band 100-4000 Hz: 1/3 semitone steps (1/36 octave)
     * Band 4000-15000 Hz: semitone steps (1/12 octave)
     *
     * @param maxFrequency upper frequency limit (must be < Nyquist)
     * @return sorted list of frequencies in Hz
     */
    public static List<Double> generateFrequencies(double maxFrequency) {
        List<Double> frequencies = new ArrayList<>();

        // Band 1: 30 Hz to 100 Hz, semitone steps
        for (int n = 0; ; n++) {
            double f = 30.0 * Math.pow(2.0, n / 12.0);
            if (f > 100.0) break;
            if (f <= maxFrequency) frequencies.add(f);
        }

        // Band 2: 100 Hz to 4000 Hz, 1/3 semitone steps
        for (int n = 1; ; n++) { // start at 1 to avoid duplicating 100 Hz
            double f = 100.0 * Math.pow(2.0, n / 36.0);
            if (f > 4000.0) break;
            if (f <= maxFrequency) frequencies.add(f);
        }

        // Band 3: 4000 Hz to 15000 Hz, semitone steps
        for (int n = 1; ; n++) { // start at 1 to avoid duplicating 4000 Hz
            double f = 4000.0 * Math.pow(2.0, n / 12.0);
            if (f > 15000.0) break;
            if (f <= maxFrequency) frequencies.add(f);
        }

        return frequencies;
    }

    /**
     * Generate with default max = 15000 Hz.
     */
    public static List<Double> generateFrequencies() {
        return generateFrequencies(15000.0);
    }

    /**
     * Calculate measurement duration for a frequency in seconds.
     * Duration = max(100 cycles / f, 0.1)
     */
    public static double getMeasurementDuration(double frequency) {
        return Math.max(100.0 / frequency, 0.1);
    }

    /**
     * Estimate total sweep time in seconds, including settling (20ms) and gaps (10ms).
     */
    public static double estimateTotalTime(List<Double> frequencies) {
        double total = 0;
        for (double f : frequencies) {
            total += getMeasurementDuration(f) + 0.020 + 0.010; // measurement + settling + gap
        }
        return total;
    }
}
