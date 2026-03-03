package com.audioanalyzer.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CalibrationResult {
    public final int delaySamples;
    public final int fineAdjustment;
    public final int totalDelay;
    public final double delayMs;
    public final Map<Double, Double> verificationPhases;

    public CalibrationResult(int delaySamples, int fineAdjustment, int sampleRate,
                             Map<Double, Double> verificationPhases) {
        this.delaySamples = delaySamples;
        this.fineAdjustment = fineAdjustment;
        this.totalDelay = delaySamples + fineAdjustment;
        this.delayMs = (double) totalDelay / sampleRate * 1000.0;
        this.verificationPhases = Collections.unmodifiableMap(
                new HashMap<>(verificationPhases));
    }

    public boolean isPhaseConsistent() {
        if (verificationPhases.size() < 2) return false;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double phase : verificationPhases.values()) {
            if (phase < min) min = phase;
            if (phase > max) max = phase;
        }
        return (max - min) <= 20.0; // ±10° total spread
    }
}
