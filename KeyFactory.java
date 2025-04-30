import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
            Consumer<String> playTone
    ) {
        JButton key = new JButton();
        key.setBounds(x, y, width, height);
        key.setBackground(isBlack ? Color.BLACK : Color.WHITE);
        key.setOpaque(true);
        key.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        keyButtons.put(note, key);

        key.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                int count = pressCount.getOrDefault(note, 0);
                if (count == 0) {
                    playTone.accept(note);
                }
                pressCount.put(note, count + 1);
                sendMessage.accept("NOTE_ON," + note + "," + timbre.get());

                if (isRecording.get()) {
                    long offset = System.currentTimeMillis() - recordingStartTime.get();
                    rawEvents.add(new String[]{"NOTE_ON", note, String.valueOf(offset), timbre.get()});
                    activeNotes.put(note, offset);
                }

                key.setBackground(isBlack ? Color.GRAY : Color.CYAN);
            }

            public void mouseReleased(MouseEvent e) {
                int count = pressCount.getOrDefault(note, 1) - 1;
                if (count <= 0) {
                    pressCount.remove(note);
                    stopTone.accept(note);
                } else {
                    pressCount.put(note, count);
                }

                sendMessage.accept("NOTE_OFF," + note + "," + timbre.get());

                if (isRecording.get() && activeNotes.containsKey(note)) {
                    long offset = System.currentTimeMillis() - recordingStartTime.get();
                    rawEvents.add(new String[]{"NOTE_OFF", note, String.valueOf(offset), timbre.get()});
                    activeNotes.remove(note);
                }

                key.setBackground(isBlack ? Color.BLACK : Color.WHITE);
            }
        });

        return key;
    }
}