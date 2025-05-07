import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;

public class KeyboardManager {
    private final int minOctave;
    private final int maxOctave;
    private final JCheckBox autoChordCheck;
    private final JComboBox<String> chordTypeSelector;
    private final Map<String, Long> activeNotes = new java.util.HashMap<>();

    // Mapping keyboard keys to note names
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

    // Map octave switch keys to their target octave
    private static final Map<Integer, Integer> OCTAVE_KEYS = Map.of(
        KeyEvent.VK_4, 4,
        KeyEvent.VK_5, 5,
        KeyEvent.VK_6, 6,
        KeyEvent.VK_7, 7
    );

    private int currentOctave;

    public KeyboardManager(int minOctave, int maxOctave, int defaultOctave,
                         JCheckBox autoChordCheck, JComboBox<String> chordTypeSelector) {
        this.minOctave = minOctave;
        this.maxOctave = maxOctave;
        this.currentOctave = defaultOctave;
        this.autoChordCheck = autoChordCheck;
        this.chordTypeSelector = chordTypeSelector;
    }
    public void handleKeyPress(KeyEvent e) {
        // System.out.println("Key pressed: " + e.getKeyCode());
        // System.out.println("Before pressing: " + PianoApp.pressCount.keySet());
    
        if (handleOctaveSwitch(e.getKeyCode())) {
            PianoApp.updateCurrentOctaveLabel(currentOctave);
            return;
        }
    
        String noteBase = KEY_TO_NOTE.get(e.getKeyCode());
        if (noteBase != null) {
            String noteName = buildNoteName(noteBase, e.getKeyCode());
    
            List<String> notesToPlay;
            if (autoChordCheck.isSelected()) {
                notesToPlay = ChordGenerator.buildChord(noteName, (String) chordTypeSelector.getSelectedItem());
            } else {
                notesToPlay = List.of(noteName);
            }
    
            for (String note : notesToPlay) {
                if (!PianoApp.WHITE_KEYS.containsKey(note) && !PianoApp.BLACK_KEYS.containsKey(note)) {
                    // System.out.println("Note out of range or unrecognized: " + note);
                    continue;
                }
    
                double freq = PianoApp.WHITE_KEYS.getOrDefault(note, PianoApp.BLACK_KEYS.getOrDefault(note, -1.0));
                if (freq > 0) {
                    if (!PianoApp.pressCount.containsKey(note)) {
                        ToneGenerator.playToneContinuous(freq, note, PianoApp.TIMBRE);
                        PianoApp.pressCount.put(note, 1);
    
                        PianoApp.sendMessage("NOTE_ON," + note + "," + PianoApp.TIMBRE);
    
                        if (PianoApp.isRecording) {
                            long offset = System.currentTimeMillis() - PianoApp.recordingStartTime;
                            synchronized (PianoApp.rawEvents) {
                                PianoApp.rawEvents.add(new String[]{"NOTE_ON", note, String.valueOf(offset), PianoApp.TIMBRE});
                            }
                            activeNotes.put(note, offset);
                        }
    
                        JButton keyBtn = PianoApp.keyButtons.get(note);
                        if (keyBtn != null) {
                            keyBtn.setBackground(note.contains("#") ? java.awt.Color.GRAY : java.awt.Color.CYAN);
                        }
                    }
                }
            }
    
            // Update the indicator
            List<String> sortedNotes = new ArrayList<>(PianoApp.pressCount.keySet());
            sortedNotes.sort(Comparator.comparingInt(KeyboardManager::noteOrder));
            String currentNotes = String.join(" ", sortedNotes);
            PianoApp.updateCurrentNoteLabel(currentNotes);
            // System.out.println("After pressing: " + PianoApp.pressCount.keySet());
        }
    }



    public void handleKeyRelease(KeyEvent e) {
        // System.out.println("Key released: " + e.getKeyCode());
        // System.out.println("Before releasing: " + PianoApp.pressCount.keySet());

        String noteBase = KEY_TO_NOTE.get(e.getKeyCode());
        if (noteBase != null) {
            String noteName = buildNoteName(noteBase, e.getKeyCode());

            List<String> notesToStop;
            if (autoChordCheck.isSelected()) {
                notesToStop = ChordGenerator.buildChord(noteName, (String) chordTypeSelector.getSelectedItem());
            } else {
                notesToStop = List.of(noteName);
            }

            for (String note : notesToStop) {
                if (PianoApp.pressCount.containsKey(note)) {
                    int count = PianoApp.pressCount.getOrDefault(note, 1) - 1;
                    if (count <= 0) {
                        PianoApp.pressCount.remove(note);
                        ToneGenerator.stopTone(note);
                    } else {
                        PianoApp.pressCount.put(note, count);
                    }
                
                    // Send network message
                    PianoApp.sendMessage("NOTE_OFF," + note + "," + PianoApp.TIMBRE);
                
                    if (PianoApp.isRecording && activeNotes.containsKey(note)) {
                        long offset = System.currentTimeMillis() - PianoApp.recordingStartTime;
                        synchronized (PianoApp.rawEvents) {
                            PianoApp.rawEvents.add(new String[]{"NOTE_OFF", note, String.valueOf(offset), PianoApp.TIMBRE});
                        }
                        activeNotes.remove(note);
                    }
                
                    JButton keyBtn = PianoApp.keyButtons.get(note);
                    if (keyBtn != null) {
                        keyBtn.setBackground(note.contains("#") ? java.awt.Color.BLACK : java.awt.Color.WHITE);
                    }
                }
            }

            // Update indicator
            if (PianoApp.pressCount.isEmpty()) {
                PianoApp.updateCurrentNoteLabel("None");
            } else {
                List<String> sortedNotes = new ArrayList<>(PianoApp.pressCount.keySet());
                sortedNotes.sort(Comparator.comparingInt(KeyboardManager::noteOrder));
                String currentNotes = String.join(" ", sortedNotes);
                PianoApp.updateCurrentNoteLabel(currentNotes);
            }
            // System.out.println("After releasing: " + PianoApp.pressCount.keySet());
        }
    }

    private String buildNoteName(String noteBase, int keyCode) {
        int targetOctave = currentOctave;
        if (noteBase.equals("C") && keyCode == KeyEvent.VK_K) {
            targetOctave = Math.min(currentOctave + 1, maxOctave);
        }
        return noteBase + targetOctave;
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
        }
    }

    public static int[] getSupportedKeyCodes() {
        return KEY_TO_NOTE.keySet().stream().mapToInt(Integer::intValue).toArray();
    }

    private static int noteOrder(String note) {
        String base = note.substring(0, note.length() - 1);
        int octave = Integer.parseInt(note.substring(note.length() - 1));

        String[] noteSequence = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int baseIndex = -1;
        for (int i = 0; i < noteSequence.length; i++) {
            if (noteSequence[i].equals(base)) {
                baseIndex = i;
                break;
            }
        }
        return octave * 100 + baseIndex;
    }
}