import java.util.*;

public class ChordGenerator {

    private static final List<String> NOTE_ORDER = Arrays.asList(
        "C4", "C#4", "D4", "D#4", "E4", "F4", "F#4", "G4", "G#4", "A4", "A#4", "B4",
        "C5", "C#5", "D5", "D#5", "E5", "F5", "F#5", "G5", "G#5", "A5", "A#5", "B5",
        "C6", "C#6", "D6", "D#6", "E6", "F6", "F#6", "G6", "G#6", "A6", "A#6", "B6",
        "C7"
    );

    public static List<String> buildChord(String root, String chordType) {
        List<String> chord = new ArrayList<>();
        int rootIndex = NOTE_ORDER.indexOf(root);
        if (rootIndex == -1) return chord;

        chordType = chordType.toLowerCase();
        int[] intervals;

        switch (chordType) {
            case "major":
                intervals = new int[]{0, 4, 7};
                break;
            case "minor":
                intervals = new int[]{0, 3, 7};
                break;
            case "diminished":
                intervals = new int[]{0, 3, 6};
                break;
            case "octave":
                intervals = new int[]{0, 12};
                break;
            default:
                return Collections.singletonList(root); // fallback
        }

        for (int interval : intervals) {
            int idx = rootIndex + interval;
            if (idx < NOTE_ORDER.size()) {
                // Prevent crossing octave boundary for major/minor/diminished
                // Check if note stays within 12 semitones of root
                if (interval <= 12) {
                    chord.add(NOTE_ORDER.get(idx));
                }
            }
        }

        return chord;
    }
}