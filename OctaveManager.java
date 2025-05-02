import java.awt.event.KeyEvent;
import java.util.Map;
import javax.swing.JButton;

public class OctaveManager {
    private final int minOctave;
    private final int maxOctave;
    private int currentOctave;

    // Mapping keyboard keys to note names (without octave number)
    private static final Map<Integer, String> KEY_TO_NOTE = Map.ofEntries(
        Map.entry(KeyEvent.VK_A, "C"),
        Map.entry(KeyEvent.VK_W, "C#"),
        Map.entry(KeyEvent.VK_S, "D"),
        Map.entry(KeyEvent.VK_E, "D#"),
        Map.entry(KeyEvent.VK_D, "E"),
        Map.entry(KeyEvent.VK_F, "F"),
        Map.entry(KeyEvent.VK_T, "F#"),
        Map.entry(KeyEvent.VK_G, "G"),
        Map.entry(KeyEvent.VK_Y, "G#"),
        Map.entry(KeyEvent.VK_H, "A"),
        Map.entry(KeyEvent.VK_U, "A#"),
        Map.entry(KeyEvent.VK_J, "B"),
        Map.entry(KeyEvent.VK_K, "C")
    );

    // Map octave switch keys to their target octave for easy extension
    private static final Map<Integer, Integer> OCTAVE_KEYS = Map.of(
        KeyEvent.VK_4, 4,
        KeyEvent.VK_5, 5,
        KeyEvent.VK_6, 6,
        KeyEvent.VK_7, 7
    );

    public OctaveManager(int minOctave, int maxOctave, int defaultOctave) {
        this.minOctave = minOctave;
        this.maxOctave = maxOctave;
        this.currentOctave = defaultOctave;
    }

    public void handleKeyPress(KeyEvent e) {
        // System.out.println("Key pressed: " + e.getKeyCode());

        // Handle octave switching
        if (handleOctaveSwitch(e.getKeyCode())) {
            PianoApp.updateCurrentOctaveLabel(currentOctave);
            return;
        }

        // Handle playing notes
        String noteBase = KEY_TO_NOTE.get(e.getKeyCode());
        if (noteBase != null) {
            String noteName = buildNoteName(noteBase, e.getKeyCode());

            if (!PianoApp.WHITE_KEYS.containsKey(noteName) && !PianoApp.BLACK_KEYS.containsKey(noteName)) {
                // System.out.println("Note out of range or unrecognized: " + noteName);
                return;
            }

            double freq = PianoApp.WHITE_KEYS.getOrDefault(noteName, PianoApp.BLACK_KEYS.getOrDefault(noteName, -1.0));
            if (freq > 0) {
                if (!PianoApp.pressCount.containsKey(noteName)) {
                    // System.out.println("First press of: " + noteName);
                    ToneGenerator.playToneContinuous(freq, noteName, PianoApp.TIMBRE);
                    PianoApp.updateCurrentNoteLabel(noteName);
                    JButton keyBtn = PianoApp.keyButtons.get(noteName);
                    if (keyBtn != null) keyBtn.setBackground(java.awt.Color.YELLOW);
                    PianoApp.pressCount.put(noteName, 1);  // mark as pressed
                } else {
                    // System.out.println("Key repeat detected for: " + noteName);
                }
            }
        }
    }

    public void handleKeyRelease(KeyEvent e) {
        // System.out.println("Key released: " + e.getKeyCode());

        String noteBase = KEY_TO_NOTE.get(e.getKeyCode());
        if (noteBase != null) {
            String noteName = buildNoteName(noteBase, e.getKeyCode());

            if (PianoApp.pressCount.containsKey(noteName)) {
                // System.out.println("Stopping tone: " + noteName);
                PianoApp.pressCount.remove(noteName);
                ToneGenerator.stopTone(noteName);
                JButton keyBtn = PianoApp.keyButtons.get(noteName);
                if (keyBtn != null)
                    keyBtn.setBackground(noteName.contains("#") ? java.awt.Color.BLACK : java.awt.Color.WHITE);
            } else {
                // System.out.println("Release ignored (note wasn't pressed): " + noteName);
            }
        }
    }

    private String buildNoteName(String noteBase, int keyCode) {
        int targetOctave = currentOctave;

        // Special case for high C (VK_K) - jumps to next octave unless at max
        if (noteBase.equals("C") && keyCode == KeyEvent.VK_K) {
            targetOctave = Math.min(currentOctave + 1, maxOctave);
        }

        String noteName = noteBase + targetOctave;
        // System.out.println("Built note name: " + noteName);
        return noteName;
    }

    private boolean handleOctaveSwitch(int keyCode) {
        Integer targetOctave = OCTAVE_KEYS.get(keyCode);
        if (targetOctave != null) {
            setCurrentOctave(targetOctave);
            return true;
        }
        return false;
    }

    private void setCurrentOctave(int octave) {
        if (octave >= minOctave && octave <= maxOctave) {
            currentOctave = octave;
            // System.out.println("Octave switched to: " + currentOctave);
        } else {
            // System.out.println("Invalid octave: " + octave);
        }
    }

    // Allow PianoApp to query which keys we support for binding
    public static int[] getSupportedKeyCodes() {
        return KEY_TO_NOTE.keySet().stream().mapToInt(Integer::intValue).toArray();
    }
}