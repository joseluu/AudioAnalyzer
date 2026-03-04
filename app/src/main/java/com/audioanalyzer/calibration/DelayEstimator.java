package com.audioanalyzer.calibration;

import java.util.ArrayList;
import java.util.List;

public class DelayEstimator {

    /** Result of a single delay estimation with quality metrics. */
    public static class EstimateResult {
        public final double delaySamples;
        public final double confidence;  // 0.0 (noise) to 1.0 (perfect match)
        public final boolean valid;

        public EstimateResult(double delaySamples, double confidence, boolean valid) {
            this.delaySamples = delaySamples;
            this.confidence = confidence;
            this.valid = valid;
        }
    }

    /** Result of a multi-burst delay estimation with quality metrics. */
    public static class MultiBurstResult {
        public final double delaySamples;
        public final double confidence;
        public final int validBursts;
        public final int totalBursts;
        public final double[] individualDelays;
        public final double[] individualConfidences;
        public final double delayStdDev;
        public final boolean valid;
        public final String rejectReason;

        public MultiBurstResult(double delaySamples, double confidence,
                                int validBursts, int totalBursts,
                                double[] individualDelays, double[] individualConfidences,
                                double delayStdDev, boolean valid, String rejectReason) {
            this.delaySamples = delaySamples;
            this.confidence = confidence;
            this.validBursts = validBursts;
            this.totalBursts = totalBursts;
            this.individualDelays = individualDelays;
            this.individualConfidences = individualConfidences;
            this.delayStdDev = delayStdDev;
            this.valid = valid;
            this.rejectReason = rejectReason;
        }
    }

    // Minimum normalized correlation to consider a burst detected
    private static final double MIN_CONFIDENCE = 0.15;
    // Maximum standard deviation (in samples) among valid bursts
    private static final double MAX_DELAY_STD_DEV = 5.0;
    // Minimum fraction of bursts that must be valid
    private static final double MIN_VALID_BURST_RATIO = 0.4;

    /**
     * Estimate delay via cross-correlation with parabolic interpolation.
     * Returns a result with confidence metric based on normalized cross-correlation.
     */
    public EstimateResult estimateDelayWithConfidence(float[] emitted, float[] received, int maxDelay) {
        if (emitted.length == 0 || received.length == 0) {
            return new EstimateResult(0, 0, false);
        }

        // Compute energy of the emitted signal
        double emittedEnergy = 0;
        for (float v : emitted) {
            emittedEnergy += v * v;
        }
        if (emittedEnergy < 1e-12) {
            return new EstimateResult(0, 0, false);
        }

        double[] corr = new double[maxDelay + 1];
        double bestCorr = -Double.MAX_VALUE;
        int bestDelay = 0;

        for (int d = 0; d <= maxDelay; d++) {
            double sum = 0;
            int count = 0;
            for (int n = 0; n < emitted.length; n++) {
                int rIdx = n + d;
                if (rIdx < received.length) {
                    sum += emitted[n] * received[rIdx];
                    count++;
                }
            }
            corr[d] = (count > 0) ? sum / count : 0;

            if (corr[d] > bestCorr) {
                bestCorr = corr[d];
                bestDelay = d;
            }
        }

        // Compute energy of received signal at best delay for normalization
        double receivedEnergy = 0;
        int count = 0;
        for (int n = 0; n < emitted.length; n++) {
            int rIdx = n + bestDelay;
            if (rIdx < received.length) {
                receivedEnergy += received[rIdx] * received[rIdx];
                count++;
            }
        }

        // Normalized cross-correlation coefficient: peak / sqrt(E_emitted * E_received)
        // Normalize per-sample to match the per-sample corr values
        double emittedEnergyNorm = emittedEnergy / emitted.length;
        double receivedEnergyNorm = (count > 0) ? receivedEnergy / count : 0;
        double normDenom = Math.sqrt(emittedEnergyNorm * receivedEnergyNorm);
        double confidence = (normDenom > 1e-12) ? bestCorr / normDenom : 0;
        // Clamp to [0, 1]
        confidence = Math.max(0, Math.min(1, confidence));

        // Parabolic interpolation for sub-sample accuracy
        double refinedDelay = bestDelay;
        if (bestDelay > 0 && bestDelay < maxDelay) {
            double a = corr[bestDelay - 1];
            double b = corr[bestDelay];
            double c = corr[bestDelay + 1];
            double denom = 2.0 * (2.0 * b - a - c);
            if (Math.abs(denom) > 1e-12) {
                double delta = (a - c) / denom;
                refinedDelay = bestDelay + delta;
            }
        }

        boolean valid = confidence >= MIN_CONFIDENCE;
        return new EstimateResult(refinedDelay, confidence, valid);
    }

    /**
     * Legacy method — returns delay only (no quality check).
     * Kept for backward compatibility with non-critical paths.
     */
    public double estimateDelay(float[] emitted, float[] received, int maxDelay) {
        return estimateDelayWithConfidence(emitted, received, maxDelay).delaySamples;
    }

    /**
     * Estimate delay from multiple bursts with quality validation.
     * Rejects low-confidence bursts, checks consistency among survivors,
     * and reports an error if no valid signal is detected.
     */
    public MultiBurstResult estimateDelayMultiBurstValidated(float[] emittedBurst, float[] received,
                                                              int burstSpacingSamples, int numBursts,
                                                              int maxDelay) {
        double[] delays = new double[numBursts];
        double[] confidences = new double[numBursts];
        int attempted = 0;

        for (int b = 0; b < numBursts; b++) {
            int recOffset = b * burstSpacingSamples;
            if (recOffset + emittedBurst.length + maxDelay > received.length) break;
            attempted++;

            int regionLen = emittedBurst.length + maxDelay;
            float[] region = new float[regionLen];
            System.arraycopy(received, recOffset, region, 0,
                    Math.min(regionLen, received.length - recOffset));

            EstimateResult result = estimateDelayWithConfidence(emittedBurst, region, maxDelay);
            delays[b] = result.delaySamples;
            confidences[b] = result.confidence;
        }

        // Collect valid bursts (above confidence threshold)
        List<Double> validDelays = new ArrayList<>();
        List<Double> validConfidences = new ArrayList<>();
        for (int b = 0; b < attempted; b++) {
            if (confidences[b] >= MIN_CONFIDENCE) {
                validDelays.add(delays[b]);
                validConfidences.add(confidences[b]);
            }
        }

        int validCount = validDelays.size();

        // Check: enough bursts detected?
        if (validCount == 0) {
            return new MultiBurstResult(0, 0, 0, attempted, delays, confidences,
                    0, false, "No signal detected — all bursts below confidence threshold");
        }

        double minRequired = attempted * MIN_VALID_BURST_RATIO;
        if (validCount < minRequired) {
            return new MultiBurstResult(0, 0, validCount, attempted, delays, confidences,
                    0, false, String.format("Only %d/%d bursts detected (need at least %.0f)",
                    validCount, attempted, Math.ceil(minRequired)));
        }

        // Compute mean and std dev of valid delays
        double sum = 0;
        double confSum = 0;
        for (int i = 0; i < validCount; i++) {
            sum += validDelays.get(i);
            confSum += validConfidences.get(i);
        }
        double mean = sum / validCount;
        double avgConfidence = confSum / validCount;

        double varianceSum = 0;
        for (int i = 0; i < validCount; i++) {
            double diff = validDelays.get(i) - mean;
            varianceSum += diff * diff;
        }
        double stdDev = (validCount > 1) ? Math.sqrt(varianceSum / (validCount - 1)) : 0;

        // Check: are the valid delays consistent?
        if (stdDev > MAX_DELAY_STD_DEV) {
            return new MultiBurstResult(mean, avgConfidence, validCount, attempted,
                    delays, confidences, stdDev, false,
                    String.format("Delay measurements inconsistent (std dev: %.1f samples)", stdDev));
        }

        return new MultiBurstResult(mean, avgConfidence, validCount, attempted,
                delays, confidences, stdDev, true, null);
    }

    /**
     * Legacy method — returns averaged delay only (no quality check).
     */
    public double estimateDelayMultiBurst(float[] emittedBurst, float[] received,
                                           int burstSpacingSamples, int numBursts,
                                           int maxDelay) {
        MultiBurstResult result = estimateDelayMultiBurstValidated(
                emittedBurst, received, burstSpacingSamples, numBursts, maxDelay);
        return result.delaySamples;
    }
}
