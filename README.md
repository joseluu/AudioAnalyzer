# Audio Path Analyzer

Android application that characterizes an audio-acoustic path (amplifier + loudspeaker) by measuring its frequency response — amplitude and phase — using quadrature synchronous detection (digital lock-in amplifier).

## Purpose

When designing or troubleshooting an audio system, you need to know how the chain (amplifier, crossover, speaker, room) modifies a signal at each frequency. This app turns an Android smartphone into a measurement instrument: it generates test tones through the headphone jack, feeds them into the device under test, and picks up the resulting sound with the built-in microphone. Synchronous detection extracts amplitude and phase with high noise rejection, producing publication-quality Bode plots from 30 Hz to 15 kHz.

## Measurement Principle

```
Headphone jack ──► Amplifier ──► Loudspeaker ──► Microphone
   (output)         (DUT)          (DUT)         (input)
```

1. The phone generates a sine wave at frequency *f* and sends it out through the headphone output (left or right channel selectable).
2. The signal passes through the amplifier under test and is reproduced by the loudspeaker.
3. The phone's microphone captures the acoustic result.
4. A digital lock-in (quadrature synchronous detector) multiplies the received signal by cosine and sine references at the same frequency *f*, yielding in-phase (I) and quadrature (Q) components:

```
I = (2/N) × Σ signal[n] × cos(2π f n/Fs + φ_ref)
Q = (2/N) × Σ signal[n] × sin(2π f n/Fs + φ_ref)

Amplitude = √(I² + Q²)
Phase     = atan2(Q, I)
```

The reference phase φ_ref compensates for the measured hardware delay so that the reported phase reflects only the device under test.

## Calibration Methods

Before measuring, the app must determine the round-trip delay between audio output and microphone input. This is done in three independent steps, each repeatable at any time.

### Step 1 — Ping Delay (burst cross-correlation)

- Emits 5 short bursts of 1 kHz sine (10 ms signal + 500 ms silence each).
- Cross-correlates each burst with the reference waveform across a 300 ms search window.
- Uses parabolic interpolation around the correlation peak for sub-sample accuracy.
- Quality gate: each burst must exceed a normalized correlation confidence of 0.15; at least 40% of bursts must pass, and surviving delays must agree within 5 samples standard deviation. If no signal is detected the user is notified.

### Step 2 — Chirp Delay (broadband refinement)

- Emits 3 exponential chirp sweeps (200 Hz → 4000 Hz, 500 ms each, separated by 500 ms silence).
- Chirps provide richer frequency content than single-tone bursts, producing a sharper correlation peak.
- Each chirp is independently cross-correlated with its reference; per-chirp delays and the average are displayed.
- Same quality validation as Step 1.

The chirp waveform uses an exponential frequency sweep:

```
f(t) = f_start × (f_end / f_start)^(t/T)
φ(t) = 2π × f_start × T / ln(R) × (e^(t·ln(R)/T) − 1)    where R = f_end / f_start
```

### Step 3 — Phase Verification (4-frequency check)

- Runs synchronous detection at 30 Hz, 100 Hz, 300 Hz, and 1000 Hz using the measured delay.
- If the phase is consistent across all four frequencies (within ±10°), the delay is correct.
- If not, a fine-adjustment slider (±50 samples) lets the user tweak the delay and re-measure individual frequencies.

## Frequency Sweep

After calibration, the app performs an automated sweep across ~232 frequency points:

| Band | Step size | Points |
|------|-----------|--------|
| 30 – 100 Hz | 1 semitone (×2^(1/12)) | ~21 |
| 100 – 4000 Hz | 1/3 semitone (×2^(1/36)) | ~188 |
| 4000 – 15000 Hz | 1 semitone (×2^(1/12)) | ~23 |

At each frequency the measurement duration is max(100 cycles / f, 100 ms), giving good averaging even at low frequencies. Total sweep time is approximately 55 seconds.

Results are displayed as amplitude (dB relative to 1 kHz) and phase (degrees) curves with selectable linear or logarithmic frequency axis. Pinch-to-zoom and scrolling are supported.

## Project Structure

```
com.audioanalyzer/
├── MainActivity                  Main screen, channel selection
├── audio/
│   ├── AudioEngine               Synchronized AudioTrack + AudioRecord
│   ├── SignalGenerator            Sine, burst, and chirp waveform generation
│   └── SyncDetector              Quadrature lock-in detector
├── calibration/
│   ├── CalibrationActivity       3-step calibration UI
│   ├── CalibrationManager        Calibration state machine and logic
│   └── DelayEstimator            Cross-correlation with quality validation
├── measurement/
│   ├── MeasurementActivity       Sweep UI with real-time chart
│   ├── FrequencySweep            Frequency list generation
│   └── MeasurementManager        Sweep orchestration
├── ui/
│   ├── ChartView                 Custom Canvas-based amplitude/phase chart
│   └── DelayAdjustView           Fine delay adjustment widget
└── model/
    ├── MeasurementPoint           (frequency, amplitude, phase)
    └── CalibrationResult          (delay, fine adjustment, verification phases)
```

## Requirements

- Android 8.0+ (API 26)
- Headphone jack or USB-C audio adapter (for electrical connection to the amplifier)
- `RECORD_AUDIO` permission (requested at runtime)

## User Guide

### Setup

1. Connect the phone's headphone output to the amplifier input using a suitable cable (3.5 mm jack or USB-C audio adapter → RCA/XLR).
2. Place the phone's microphone in front of the loudspeaker, ideally on-axis at the listening position.
3. Set the amplifier to a moderate volume — you should hear the test tones clearly without distortion.

### Calibration

1. Launch the app and select the output channel (**Left** or **Right**) matching the amplifier input you connected.
2. The screen displays the device sample rate (typically 48000 Hz).
3. Tap **Measure Ping Delay**.
   - The app plays short beeps and measures the round-trip delay.
   - If "No signal detected" appears, check cable connections and volume, then retry.
   - On success the delay is displayed in samples and milliseconds.
4. Tap **Measure Chirp Delay**.
   - The app plays three rising sweeps and refines the delay estimate.
   - Individual chirp delays and the average are shown.
5. Tap **Run Phase Verification**.
   - Four frequencies are measured. If all phases are close (within ~10°), the delay is correct.
   - If phases diverge, use the **Fine delay adjustment** slider and tap individual **Re-measure** buttons until the phases converge.
6. Tap **Validate & Proceed to Measurement**.

### Measurement

1. Tap **Start** to begin the frequency sweep.
2. A progress bar shows the current frequency and completion percentage.
3. When complete, amplitude and phase curves are displayed.
   - Toggle **Lin/Log** for frequency axis scaling.
   - Pinch to zoom, scroll to navigate.
   - Tap a point for exact values.
4. Tap **Export CSV** to save the data as a semicolon-delimited file (`Documents/AudioAnalyzer/`). The file can be shared via the Android share menu.

### Tips

- Keep the environment quiet during measurement — the lock-in detector rejects noise well, but very loud background noise can affect low-frequency readings.
- If the phase verification shows large differences, the delay estimate may be off by a few samples. Use the fine adjustment slider to correct it.
- You can return to any calibration step at any time without restarting the whole process.
- For repeatable measurements, keep the microphone position fixed between runs.

## Data Export

CSV files use the format:

```
frequency_Hz;amplitude_lin;amplitude_dB;phase_deg
30.000;0.0234;-32.6;12.3
31.775;0.0251;-32.0;11.8
...
```

Amplitude in dB is relative to the amplitude measured at 1 kHz. Phase is in degrees relative to the phase at 1 kHz.
