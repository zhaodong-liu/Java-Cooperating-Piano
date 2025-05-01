import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.*;

public class KeyFactory {

    public static JButton createKey(
            String note,
            double freq,
            boolean isBlack,
            int x, int y, int width, int height,
            Map<String, JButton> keyButtons,
            Map<String, Integer> pressCount,
            List<String[]> rawEvents,
            Map<String, Long> activeNotes,
            Supplier<Long> recordingStartTime,
            Supplier<Boolean> isRecording,
            Supplier<String> timbre,
            Consumer<String> sendMessage,
            Consumer<String> stopTone,
            Consumer<String> playTone,
            Supplier<Boolean> isAutoChordEnabled,
            Supplier<String> chordType
    ) {
        JButton key = new JButton();
        key.setBounds(x, y, width, height);
        key.setBackground(isBlack ? Color.BLACK : Color.WHITE);
        key.setOpaque(true);
        key.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        keyButtons.put(note, key);

        key.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                List<String> notesToPlay = isAutoChordEnabled.get()
                        ? ChordGenerator.buildChord(note, chordType.get())
                        : List.of(note);

                for (String n : notesToPlay) {
                    int count = pressCount.getOrDefault(n, 0);
                    if (count == 0) {
                        playTone.accept(n);
                    }
                    pressCount.put(n, count + 1);
                    sendMessage.accept("NOTE_ON," + n + "," + timbre.get());

                    if (isRecording.get()) {
                        long offset = System.currentTimeMillis() - recordingStartTime.get();
                        rawEvents.add(new String[]{"NOTE_ON", n, String.valueOf(offset), timbre.get()});
                        activeNotes.put(n, offset);
                    }

                    JButton btn = keyButtons.get(n);
                    if (btn != null) {
                        btn.setBackground(n.contains("#") ? Color.GRAY : Color.CYAN);
                    }
                }
                String currentNotes = String.join(" ", pressCount.keySet());
                PianoApp.updateCurrentNoteLabel(currentNotes);
            }

            public void mouseReleased(MouseEvent e) {
                List<String> notesToStop = isAutoChordEnabled.get()
                        ? ChordGenerator.buildChord(note, chordType.get())
                        : List.of(note);

                for (String n : notesToStop) {
                    int count = pressCount.getOrDefault(n, 1) - 1;
                    if (count <= 0) {
                        pressCount.remove(n);
                        stopTone.accept(n);
                    } else {
                        pressCount.put(n, count);
                    }

                    sendMessage.accept("NOTE_OFF," + n + "," + timbre.get());

                    if (isRecording.get() && activeNotes.containsKey(n)) {
                        long offset = System.currentTimeMillis() - recordingStartTime.get();
                        rawEvents.add(new String[]{"NOTE_OFF", n, String.valueOf(offset), timbre.get()});
                        activeNotes.remove(n);
                    }

                    JButton btn = keyButtons.get(n);
                    if (btn != null) {
                        btn.setBackground(n.contains("#") ? Color.BLACK : Color.WHITE);
                    }
                }
                if (pressCount.isEmpty()) {
                    PianoApp.updateCurrentNoteLabel("None");
                } else {
                    String currentNotes = String.join(" ", pressCount.keySet());
                    PianoApp.updateCurrentNoteLabel(currentNotes);
                }
            }
        });

        return key;
    }
}