package com.audioanalyzer.model;

public class MeasurementPoint {
    public final double frequency;
    public final double amplitude;
    public final double phaseDegrees;
    public final long timestamp;

    public MeasurementPoint(double frequency, double amplitude, double phaseDegrees) {
        this.frequency = frequency;
        this.amplitude = amplitude;
        this.phaseDegrees = phaseDegrees;
        this.timestamp = System.currentTimeMillis();
    }

    public double getAmplitudeDb(double referenceAmplitude) {
        if (referenceAmplitude <= 0 || amplitude <= 0) return -100.0;
        return 20.0 * Math.log10(amplitude / referenceAmplitude);
    }

    public double getRelativePhase(double referencePhase) {
        double rel = phaseDegrees - referencePhase;
        while (rel > 180.0) rel -= 360.0;
        while (rel < -180.0) rel += 360.0;
        return rel;
    }
}
