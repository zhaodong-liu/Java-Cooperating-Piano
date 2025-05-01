import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sound.sampled.*;

public class ToneGenerator {
    private static final float SAMPLE_RATE = 44100f;
    private static final Map<String, SourceDataLine> playingLines = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> keyPressed = new ConcurrentHashMap<>();
    private static final Map<String, Thread> keyThreads = new ConcurrentHashMap<>();
    private static final Map<String, String> currentTimbre = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> pianoSamples = new ConcurrentHashMap<>();
    private static final Map<String, Integer> pianoOffsets = new ConcurrentHashMap<>();
    private static volatile double globalVolume = 0.5; // Default 50%

    public static void setGlobalVolume(double volume) {
        globalVolume = Math.max(0.0, Math.min(1.0, volume));
    }

    public static void loadPianoSamples() {
        String[] notes = {
            "C4", "C#4", "D4", "D#4", "E4", "F4", "F#4", "G4", "G#4", "A4", "A#4", "B4",
            "C5", "C#5", "D5", "D#5", "E5", "F5", "F#5", "G5", "G#5", "A5", "A#5", "B5",
            "C6", "C#6", "D6", "D#6", "E6", "F6", "F#6", "G6", "G#6", "A6", "A#6", "B6", "C7"
        };
        for (String note : notes) {
            try {
                File file = new File("piano_samples/" + note + ".wav");
                AudioInputStream ais = AudioSystem.getAudioInputStream(file);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int read;
                while ((read = ais.read(buffer)) != -1) {
                    baos.write(buffer, 0, read);
                }
                pianoSamples.put(note, baos.toByteArray());
                System.out.println("Loaded sample for " + note);
            } catch (Exception e) {
                System.err.println("Failed to load sample for " + note);
            }
        }
    }

    public static void initializeKeys(Set<String> allKeys) {
        for (String key : allKeys) {
            keyPressed.put(key, false);
            currentTimbre.put(key, "sine");

            Thread thread = new Thread(() -> {
                try {
                    AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
                    SourceDataLine line = AudioSystem.getSourceDataLine(format);
                    line.open(format);
                    line.start();
                    playingLines.put(key, line);

                    double freq = getFrequency(key);
                    double phase = 0.0;
                    double phaseIncrement = 2.0 * Math.PI * freq / SAMPLE_RATE;

                    int smallChunkSize = 256;
                    boolean wasPressed = false;

                    while (true) {
                        String timbre = currentTimbre.getOrDefault(key, "sine");

                        if (keyPressed.getOrDefault(key, false)) {
                            if (!wasPressed) {
                                line.start();
                                wasPressed = true;

                                if ("piano".equals(timbre)) {
                                    pianoOffsets.put(key, 0);
                                }
                            }

                            byte[] buffer = new byte[2 * smallChunkSize];

                            if ("piano".equals(timbre)) {
                                byte[] sample = pianoSamples.get(key);
                                int offset = pianoOffsets.getOrDefault(key, 0);
                                if (sample != null && offset < sample.length) {
                                    int bytesToWrite = Math.min(buffer.length, sample.length - offset);
                                    for (int i = 0; i < bytesToWrite; i += 2) {
                                        if (offset + i + 1 >= sample.length) break;
                                    
                                        // 从原始sample读取16-bit little endian
                                        int low = sample[offset + i] & 0xff;
                                        int high = sample[offset + i + 1];
                                        short origSample = (short) ((high << 8) | low);
                                    
                                        // 缩放音量
                                        short scaledSample = (short) (origSample * globalVolume);
                                    
                                        buffer[i] = (byte) (scaledSample & 0xff);
                                        buffer[i + 1] = (byte) ((scaledSample >> 8) & 0xff);
                                    }
                                    line.write(buffer, 0, bytesToWrite);
                                    pianoOffsets.put(key, offset + bytesToWrite);
                                } else {
                                    keyPressed.put(key, false); // sample finished
                                }
                            } else {
                                for (int i = 0; i < smallChunkSize; i++) {
                                    double wave = generateWave(phase, timbre);
                                    short sample = (short) (wave * globalVolume * Short.MAX_VALUE);
                                    buffer[2 * i] = (byte) (sample & 0xff);
                                    buffer[2 * i + 1] = (byte) ((sample >> 8) & 0xff);

                                    phase += phaseIncrement;
                                    if (phase >= 2.0 * Math.PI) {
                                        phase -= 2.0 * Math.PI;
                                    }
                                }
                                line.write(buffer, 0, buffer.length);
                            }

                        } else {
                            if (wasPressed) {
                                line.stop();
                                line.flush();
                                wasPressed = false;
                            }
                            Thread.sleep(5);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            thread.start();
            keyThreads.put(key, thread);
        }
    }

    public static void playToneContinuous(double freq, String key, String timbre) {
        keyPressed.put(key, true);
        currentTimbre.put(key, timbre);
    }

    public static void stopTone(String key) {
        keyPressed.put(key, false);
    }

    public static void stopAllTones() {
        for (String key : keyPressed.keySet()) {
            stopTone(key);
        }
    }

    private static double getFrequency(String key) {
        if (PianoApp.BLACK_KEYS.containsKey(key)) {
            return PianoApp.BLACK_KEYS.get(key);
        } else {
            return PianoApp.WHITE_KEYS.get(key);
        }
    }

    private static double generateWave(double phase, String timbre) {
        switch (timbre) {
            case "square":
                return Math.signum(Math.sin(phase));
            case "triangle":
                return 2.0 / Math.PI * Math.asin(Math.sin(phase));
            case "sawtooth":
                return 2.0 * (phase / (2.0 * Math.PI)) - 1.0;
            case "sine":
            default:
                return Math.sin(phase);
        }
    }
}