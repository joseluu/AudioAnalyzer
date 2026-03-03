package com.audioanalyzer.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

public class AudioEngine {
    private static final String TAG = "AudioEngine";

    private final int sampleRate;
    private final boolean useFloat;
    private AudioTrack audioTrack;
    private AudioRecord audioRecord;
    private boolean leftChannel = true;

    private volatile boolean running = false;
    private Thread writeThread;
    private Thread readThread;

    // Pre-allocated buffers
    private float[] writeBufferFloat;
    private short[] writeBuffer16;
    private float[] readBufferFloat;
    private short[] readBuffer16;
    private float[] stereoWriteBuffer;

    private final int playBufferSize;
    private final int recordBufferSize;
    private final int framesPerBuffer;

    // Callback for providing output samples and receiving input samples
    private AudioCallback callback;

    public interface AudioCallback {
        /** Fill monoBuffer with samples to play. Called from write thread. */
        void onWriteBuffer(float[] monoBuffer, int numFrames);
        /** Process received mono samples. Called from read thread. */
        void onReadBuffer(float[] monoBuffer, int numFrames);
    }

    public AudioEngine(int sampleRate) {
        this.sampleRate = sampleRate;
        this.useFloat = true; // API 26+ supports float

        int minPlay = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_FLOAT);
        playBufferSize = minPlay * 2;

        int minRecord = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT);
        recordBufferSize = minRecord * 2;

        // Frames per buffer for mono write callback
        framesPerBuffer = playBufferSize / (2 * 4); // stereo float = 2ch * 4bytes

        // Pre-allocate buffers
        writeBufferFloat = new float[framesPerBuffer];
        stereoWriteBuffer = new float[framesPerBuffer * 2];
        readBufferFloat = new float[recordBufferSize / 4];
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public boolean isLeftChannel() {
        return leftChannel;
    }

    public void setLeftChannel(boolean leftChannel) {
        this.leftChannel = leftChannel;
    }

    public void setCallback(AudioCallback callback) {
        this.callback = callback;
    }

    public void start() {
        if (running) return;

        createAudioTrack();
        createAudioRecord();

        running = true;

        readThread = new Thread(this::readLoop, "AudioRead");
        readThread.setPriority(Thread.MAX_PRIORITY);
        readThread.start();

        writeThread = new Thread(this::writeLoop, "AudioWrite");
        writeThread.setPriority(Thread.MAX_PRIORITY);
        writeThread.start();

        // Start both as close together as possible
        audioRecord.startRecording();
        audioTrack.play();
    }

    public void stop() {
        running = false;

        if (writeThread != null) {
            try { writeThread.join(1000); } catch (InterruptedException ignored) {}
            writeThread = null;
        }
        if (readThread != null) {
            try { readThread.join(1000); } catch (InterruptedException ignored) {}
            readThread = null;
        }

        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception e) {
                Log.w(TAG, "Error stopping AudioTrack", e);
            }
            audioTrack = null;
        }
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {
                Log.w(TAG, "Error stopping AudioRecord", e);
            }
            audioRecord = null;
        }
    }

    public boolean isRunning() {
        return running;
    }

    private void createAudioTrack() {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .build();

        audioTrack = new AudioTrack(attrs, format, playBufferSize,
                AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
    }

    @SuppressWarnings("MissingPermission")
    private void createAudioRecord() {
        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .build();

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                recordBufferSize);
    }

    private void writeLoop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

        while (running) {
            if (callback != null) {
                callback.onWriteBuffer(writeBufferFloat, framesPerBuffer);
            } else {
                java.util.Arrays.fill(writeBufferFloat, 0f);
            }

            // Interleave mono to stereo with channel selection
            for (int i = 0; i < framesPerBuffer; i++) {
                if (leftChannel) {
                    stereoWriteBuffer[i * 2] = writeBufferFloat[i];
                    stereoWriteBuffer[i * 2 + 1] = 0f;
                } else {
                    stereoWriteBuffer[i * 2] = 0f;
                    stereoWriteBuffer[i * 2 + 1] = writeBufferFloat[i];
                }
            }

            audioTrack.write(stereoWriteBuffer, 0, framesPerBuffer * 2,
                    AudioTrack.WRITE_BLOCKING);
        }
    }

    private void readLoop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

        while (running) {
            int read = audioRecord.read(readBufferFloat, 0, readBufferFloat.length,
                    AudioRecord.READ_BLOCKING);
            if (read > 0 && callback != null) {
                callback.onReadBuffer(readBufferFloat, read);
            }
        }
    }

    /**
     * Get native sample rate from AudioManager.
     */
    public static int getNativeSampleRate(AudioManager audioManager) {
        String srStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        if (srStr != null) {
            try {
                return Integer.parseInt(srStr);
            } catch (NumberFormatException ignored) {}
        }
        return 44100; // fallback
    }
}
