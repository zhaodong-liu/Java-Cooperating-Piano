import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sound.sampled.*;

public class ToneGenerator {
    private static final float SAMPLE_RATE = 44100f;

    private static final Map<String, SourceDataLine> playingLines = new ConcurrentHashMap<>();
    private static final Map<String, Thread> playingThreads = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> stopRequested = new ConcurrentHashMap<>();

    public static void playToneContinuous(double freq, String key, String timbre) {
        Thread thread = new Thread(() -> {
            try {
                AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
                SourceDataLine line = AudioSystem.getSourceDataLine(format);
                line.open(format);
                line.start();

                // After fully opening, check if someone requested stop
                if (stopRequested.remove(key) != null) {
                    line.stop();
                    line.close();
                    return;
                }

                playingLines.put(key, line);

                byte[] buffer = generateWaveform(freq, 100, timbre);
                while (!Thread.currentThread().isInterrupted()) {
                    line.write(buffer, 0, buffer.length);
                }

                line.drain();
                line.stop();
                line.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                playingLines.remove(key);
                playingThreads.remove(key);
            }
        });

        thread.start();
        playingThreads.put(key, thread);
    }

    public static void stopTone(String key) {
        // If the line hasn't started yet, record the stop request
        if (!playingLines.containsKey(key)) {
            stopRequested.put(key, true);
        }

        Thread thread = playingThreads.remove(key);
        if (thread != null) {
            thread.interrupt();
        }

        SourceDataLine line = playingLines.remove(key);
        if (line != null) {
            line.stop();
            line.flush();
            line.close();
        }
    }

    public static void stopAllTones() {
        // Interrupt all threads
        for (Thread thread : playingThreads.values()) {
            if (thread != null) {
                thread.interrupt();
            }
        }
        playingThreads.clear();

        // Stop and close all audio lines
        for (SourceDataLine line : playingLines.values()) {
            if (line != null) {
                line.stop();
                line.flush();
                line.close();
            }
        }
        playingLines.clear();

        // Clear any pending stop requests
        stopRequested.clear();
    }

    private static byte[] generateWaveform(double freq, int durationMs, String timbre) {
        double volume = 0.5;
        int cycles = (int) Math.max(1, Math.round(freq * durationMs / 1000.0));
        int samplesPerCycle = (int) (SAMPLE_RATE / freq);
        int numSamples = samplesPerCycle * cycles;
        byte[] buffer = new byte[2 * numSamples];

        for (int i = 0; i < numSamples; i++) {
            double angle = 2.0 * Math.PI * i * freq / SAMPLE_RATE;
            double wave;

            switch (timbre) {
                case "square":
                    wave = Math.signum(Math.sin(angle));
                    break;
                case "triangle":
                    wave = 2 / Math.PI * Math.asin(Math.sin(angle));
                    break;
                case "sawtooth":
                    wave = 2 * (i * freq / SAMPLE_RATE - Math.floor(i * freq / SAMPLE_RATE + 0.5));
                    break;
                default:
                    wave = Math.sin(angle);
            }

            int fadeSamples = (int) (SAMPLE_RATE * 0.005);
            double fadeFactor = 1.0;
            if (i < fadeSamples) {
                fadeFactor = i / (double) fadeSamples;
            } else if (i > numSamples - fadeSamples) {
                fadeFactor = (numSamples - i) / (double) fadeSamples;
            }

            wave *= volume * fadeFactor;
            short sample = (short) (wave * Short.MAX_VALUE);
            buffer[2 * i] = (byte) (sample & 0xff);
            buffer[2 * i + 1] = (byte) ((sample >> 8) & 0xff);
        }

        return buffer;
    }
}