import java.util.HashMap;
import java.util.Map;
import javax.sound.sampled.*;

public class ToneGenerator {
    private static final float SAMPLE_RATE = 44100f;
    private static final Map<String, SourceDataLine> playingLines = new HashMap<>();


    public static void playToneContinuous(double freq, String key, String timbre) {
        Thread thread = new Thread(() -> {
            try {
                AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
                SourceDataLine line = AudioSystem.getSourceDataLine(format);
                line.open(format);
                line.start();
                playingLines.put(key, line);

                byte[] buffer = generateWaveform(freq, 2000, timbre); // looped short buffer
                while (!Thread.currentThread().isInterrupted()) {
                    line.write(buffer, 0, buffer.length);
                }

                line.drain();
                line.stop();
                line.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.start();
    }

    public static void stopTone(String key) {
        SourceDataLine line = playingLines.remove(key);
        if (line != null) {
            line.stop();
            line.flush();
            line.close();
        }
    }

    private static byte[] generateWaveform(double freq, int durationMs, String timbre) {
        double volume = 0.5;
        int cycles = (int) Math.max(1, Math.round(freq * durationMs / 1000.0));  // full wave cycles
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
    
            // Apply fade-in/out (5ms ramp)
            int fadeSamples = (int) (SAMPLE_RATE * 0.005);  // 5ms
            double fadeFactor = 1.0;
            if (i < fadeSamples) {
                fadeFactor = i / (double) fadeSamples;  // fade-in
            } else if (i > numSamples - fadeSamples) {
                fadeFactor = (numSamples - i) / (double) fadeSamples;  // fade-out
            }
    
            wave *= volume * fadeFactor;
            short sample = (short) (wave * Short.MAX_VALUE);
            buffer[2 * i] = (byte) (sample & 0xff);
            buffer[2 * i + 1] = (byte) ((sample >> 8) & 0xff);
        }
    
        return buffer;
    }
}