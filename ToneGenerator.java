import java.util.*;
import java.util.concurrent.*;
import javax.sound.sampled.*;

public class ToneGenerator {
    private static final float SAMPLE_RATE = 44100f;
    private static final Map<String, SourceDataLine> playingLines = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> keyPressed = new ConcurrentHashMap<>();
    private static final Map<String, Thread> keyThreads = new ConcurrentHashMap<>();
    private static final Map<String, String> currentTimbre = new ConcurrentHashMap<>();
    private static volatile double globalVolume = 0.5; // Default 50%

    public static void setGlobalVolume(double volume) {
        globalVolume = Math.max(0.0, Math.min(1.0, volume)); // Clamp between 0 and 1
}

    public static void initializeKeys(Set<String> allKeys) {
        for (String key : allKeys) {
            keyPressed.put(key, false);
            currentTimbre.put(key, "sine"); // default timbre

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

                    int smallChunkSize = 2048; // good balance (~46ms)
                    int fadeSamples = (int)(SAMPLE_RATE * 0.005); // 5ms fade = 220 samples

                    boolean wasPressed = false;
                    boolean fadingIn = false;
                    boolean fadingOut = false;
                    boolean requestedStop = false;
                    double fadeVolume = 1.0; // 1.0 = full volume, 0.0 = silent

                    while (true) {
                        if (keyPressed.getOrDefault(key, false)) {
                            if (!wasPressed) {
                                line.start();
                                wasPressed = true;
                                fadingIn = true;
                                fadeVolume = 0.0;
                            }

                            byte[] buffer = new byte[2 * smallChunkSize];
                            String timbre = currentTimbre.getOrDefault(key, "sine");

                            for (int i = 0; i < smallChunkSize; i++) {
                                double wave = generateWave(phase, timbre);

                                // Apply fade-in
                                if (fadingIn) {
                                    fadeVolume += 1.0 / fadeSamples;
                                    if (fadeVolume >= 1.0) {
                                        fadeVolume = 1.0;
                                        fadingIn = false;
                                    }
                                }

                                short sample = (short) (wave * globalVolume * fadeVolume * Short.MAX_VALUE);
                                buffer[2 * i] = (byte) (sample & 0xff);
                                buffer[2 * i + 1] = (byte) ((sample >> 8) & 0xff);

                                phase += phaseIncrement;
                                if (phase >= 2.0 * Math.PI) {
                                    phase -= 2.0 * Math.PI;
                                }
                            }

                            line.write(buffer, 0, buffer.length);

                        } else {
                            if (wasPressed && !fadingOut) {
                                fadingOut = true;
                                fadeVolume = 1.0;
                                requestedStop = true;
                            }

                            if (fadingOut) {
                                byte[] buffer = new byte[2 * smallChunkSize];
                                String timbre = currentTimbre.getOrDefault(key, "sine");

                                for (int i = 0; i < smallChunkSize; i++) {
                                    double wave = generateWave(phase, timbre);

                                    fadeVolume -= 1.0 / fadeSamples;
                                    if (fadeVolume <= 0.0) {
                                        fadeVolume = 0.0;
                                    }

                                    short sample = (short) (wave * globalVolume * fadeVolume * Short.MAX_VALUE);
                                    buffer[2 * i] = (byte) (sample & 0xff);
                                    buffer[2 * i + 1] = (byte) ((sample >> 8) & 0xff);

                                    phase += phaseIncrement;
                                    if (phase >= 2.0 * Math.PI) {
                                        phase -= 2.0 * Math.PI;
                                    }
                                }

                                line.write(buffer, 0, buffer.length);

                                if (fadeVolume <= 0.0) {
                                    line.stop();
                                    line.flush();
                                    wasPressed = false;
                                    fadingOut = false;
                                    requestedStop = false;
                                }
                            } else {
                                Thread.sleep(5);
                            }
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