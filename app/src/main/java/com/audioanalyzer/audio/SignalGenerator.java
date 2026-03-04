package com.audioanalyzer.audio;

public class SignalGenerator {

    private static final float DEFAULT_AMPLITUDE = 0.8f;

    /**
     * Fill buffer with a sine wave, maintaining phase continuity.
     * @return final phase for next call
     */
    public double generateSine(float[] buffer, double frequency, int sampleRate,
                                double phase, float amplitude) {
        double phaseIncrement = 2.0 * Math.PI * frequency / sampleRate;
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = (float) (amplitude * Math.sin(phase));
            phase += phaseIncrement;
        }
        // Keep phase in [0, 2π) to avoid precision loss
        phase %= (2.0 * Math.PI);
        if (phase < 0) phase += 2.0 * Math.PI;
        return phase;
    }

    public double generateSine(float[] buffer, double frequency, int sampleRate, double phase) {
        return generateSine(buffer, frequency, sampleRate, phase, DEFAULT_AMPLITUDE);
    }

    /**
     * Fill a stereo buffer with signal on one channel, silence on the other.
     * @param stereoBuffer interleaved L/R stereo buffer (length = 2 * mono samples)
     * @param leftChannel  true = signal on left, false = signal on right
     * @return final phase
     */
    public double generateStereo(float[] stereoBuffer, double frequency, int sampleRate,
                                  double phase, float amplitude, boolean leftChannel) {
        double phaseIncrement = 2.0 * Math.PI * frequency / sampleRate;
        int monoSamples = stereoBuffer.length / 2;
        for (int i = 0; i < monoSamples; i++) {
            float sample = (float) (amplitude * Math.sin(phase));
            if (leftChannel) {
                stereoBuffer[i * 2] = sample;
                stereoBuffer[i * 2 + 1] = 0f;
            } else {
                stereoBuffer[i * 2] = 0f;
                stereoBuffer[i * 2 + 1] = sample;
            }
            phase += phaseIncrement;
        }
        phase %= (2.0 * Math.PI);
        if (phase < 0) phase += 2.0 * Math.PI;
        return phase;
    }

    /**
     * Generate a burst: signalSamples of sine then silenceSamples of zeros.
     * Applies 1ms fade-in/out at burst edges.
     */
    public void generateBurst(float[] buffer, double frequency, int sampleRate,
                               double phase, float amplitude,
                               int signalSamples, int silenceSamples) {
        int fadeSamples = sampleRate / 1000; // 1ms fade
        if (fadeSamples > signalSamples / 2) fadeSamples = signalSamples / 2;

        double phaseIncrement = 2.0 * Math.PI * frequency / sampleRate;
        int totalSamples = Math.min(buffer.length, signalSamples + silenceSamples);

        for (int i = 0; i < totalSamples; i++) {
            if (i < signalSamples) {
                float sample = (float) (amplitude * Math.sin(phase));
                // Fade-in
                if (i < fadeSamples) {
                    sample *= (float) i / fadeSamples;
                }
                // Fade-out
                if (i >= signalSamples - fadeSamples) {
                    sample *= (float) (signalSamples - 1 - i) / fadeSamples;
                }
                buffer[i] = sample;
                phase += phaseIncrement;
            } else {
                buffer[i] = 0f;
            }
        }
    }

    /**
     * Generate an exponential chirp sweep from startFreq to endFreq.
     * Instantaneous frequency: f(t) = startFreq * R^(t/T) where R = endFreq/startFreq
     * Phase: φ(t) = 2π * startFreq * T / ln(R) * (e^(t*ln(R)/T) - 1)
     * Applies 1ms fade-in/out at edges.
     */
    public void generateChirp(float[] buffer, int sampleRate, double startFreq,
                               double endFreq, int durationSamples, float amplitude) {
        int fadeSamples = sampleRate / 1000; // 1ms fade
        if (fadeSamples > durationSamples / 2) fadeSamples = durationSamples / 2;

        double T = (double) durationSamples / sampleRate;
        double R = endFreq / startFreq;
        double lnR = Math.log(R);

        int len = Math.min(buffer.length, durationSamples);
        for (int i = 0; i < len; i++) {
            double t = (double) i / sampleRate;
            double phase = 2.0 * Math.PI * startFreq * T / lnR * (Math.exp(t * lnR / T) - 1.0);
            float sample = (float) (amplitude * Math.sin(phase));

            // Fade-in
            if (i < fadeSamples) {
                sample *= (float) i / fadeSamples;
            }
            // Fade-out
            if (i >= durationSamples - fadeSamples) {
                sample *= (float) (durationSamples - 1 - i) / fadeSamples;
            }
            buffer[i] = sample;
        }
        // Zero-fill remainder
        for (int i = len; i < buffer.length; i++) {
            buffer[i] = 0f;
        }
    }

    /**
     * Generate burst in stereo format.
     */
    public void generateStereoBurst(float[] stereoBuffer, double frequency, int sampleRate,
                                     double phase, float amplitude,
                                     int signalSamples, int silenceSamples,
                                     boolean leftChannel) {
        int fadeSamples = sampleRate / 1000;
        if (fadeSamples > signalSamples / 2) fadeSamples = signalSamples / 2;

        double phaseIncrement = 2.0 * Math.PI * frequency / sampleRate;
        int monoTotal = Math.min(stereoBuffer.length / 2, signalSamples + silenceSamples);

        for (int i = 0; i < monoTotal; i++) {
            float sample = 0f;
            if (i < signalSamples) {
                sample = (float) (amplitude * Math.sin(phase));
                if (i < fadeSamples) {
                    sample *= (float) i / fadeSamples;
                }
                if (i >= signalSamples - fadeSamples) {
                    sample *= (float) (signalSamples - 1 - i) / fadeSamples;
                }
                phase += phaseIncrement;
            }
            if (leftChannel) {
                stereoBuffer[i * 2] = sample;
                stereoBuffer[i * 2 + 1] = 0f;
            } else {
                stereoBuffer[i * 2] = 0f;
                stereoBuffer[i * 2 + 1] = sample;
            }
        }
    }
}
