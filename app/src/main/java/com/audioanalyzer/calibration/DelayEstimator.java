package com.audioanalyzer.calibration;

public class DelayEstimator {

    /**
     * Estimate delay via cross-correlation with parabolic interpolation.
     *
     * @param emitted  emitted signal
     * @param received received signal (typically longer due to delay + silence)
     * @param maxDelay maximum delay to search (in samples)
     * @return estimated delay in samples (sub-sample resolution)
     */
    public double estimateDelay(float[] emitted, float[] received, int maxDelay) {
        if (emitted.length == 0 || received.length == 0) return 0;

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

        // Parabolic interpolation for sub-sample accuracy
        if (bestDelay > 0 && bestDelay < maxDelay) {
            double a = corr[bestDelay - 1];
            double b = corr[bestDelay];
            double c = corr[bestDelay + 1];
            double denom = 2.0 * (2.0 * b - a - c);
            if (Math.abs(denom) > 1e-12) {
                double delta = (a - c) / denom;
                return bestDelay + delta;
            }
        }

        return bestDelay;
    }

    /**
     * Estimate delay from multiple bursts and average.
     *
     * @param emittedBurst single burst reference (mono)
     * @param received     full recording containing multiple bursts
     * @param burstSpacingSamples total samples per burst cycle (signal + silence)
     * @param numBursts    number of bursts to average
     * @param maxDelay     max delay to search
     * @return averaged delay estimate
     */
    public double estimateDelayMultiBurst(float[] emittedBurst, float[] received,
                                           int burstSpacingSamples, int numBursts,
                                           int maxDelay) {
        double totalDelay = 0;
        int validBursts = 0;

        for (int b = 0; b < numBursts; b++) {
            int recOffset = b * burstSpacingSamples;
            if (recOffset + emittedBurst.length + maxDelay > received.length) break;

            // Extract the region of received signal for this burst
            int regionLen = emittedBurst.length + maxDelay;
            float[] region = new float[regionLen];
            System.arraycopy(received, recOffset, region, 0,
                    Math.min(regionLen, received.length - recOffset));

            double delay = estimateDelay(emittedBurst, region, maxDelay);
            totalDelay += delay;
            validBursts++;
        }

        return (validBursts > 0) ? totalDelay / validBursts : 0;
    }
}
