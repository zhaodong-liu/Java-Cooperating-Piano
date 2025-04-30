import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.*;

public class PlaybackManager {
    private final Map<String, Double> whiteKeys;
    private final Map<String, Double> blackKeys;
    private final Map<String, JButton> keyButtons;
    private final JProgressBar playbackBar;
    private final JButton playResumeBtn;

    private final Set<String> activePlaybackNotes = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> activeNoteEndTimes = new ConcurrentHashMap<>();
    private final Map<String, String> activeNoteTimbres = new ConcurrentHashMap<>();
    private final Map<String, Long> activeNoteStartTimes = new ConcurrentHashMap<>();
    private final AtomicLong playbackStart = new AtomicLong();
    private final Object playbackLock = new Object();

    private volatile boolean isPaused = false;
    private volatile boolean stopRequested = false;

    private long pauseStartTime = 0;
    private long totalPausedTime = 0;

    public PlaybackManager(Map<String, Double> whiteKeys,
                           Map<String, Double> blackKeys,
                           Map<String, JButton> keyButtons,
                           JProgressBar playbackBar,
                           JButton playResumeBtn) {
        this.whiteKeys = whiteKeys;
        this.blackKeys = blackKeys;
        this.keyButtons = keyButtons;
        this.playbackBar = playbackBar;
        this.playResumeBtn = playResumeBtn;
    }

    public void play(List<String[]> events) {
        if (events.isEmpty()) return;

        stopRequested = false;
        isPaused = false;
        totalPausedTime = 0;
        pauseStartTime = 0;
        playbackStart.set(System.currentTimeMillis());

        new Thread(() -> runPlayback(events)).start();
    }

    
    public void togglePause() {
        synchronized (playbackLock) {
            isPaused = !isPaused;
    
            if (isPaused) {
                pauseStartTime = System.currentTimeMillis();
                SwingUtilities.invokeLater(() -> playResumeBtn.setText("▶"));
    
                // === Stop all currently playing notes ===
                long logicalNow = pauseStartTime - playbackStart.get() - totalPausedTime;
    
                for (String note : new HashSet<>(activePlaybackNotes)) {
                    long end = activeNoteEndTimes.getOrDefault(note, logicalNow);
                    long remaining = end - logicalNow;
    
                    if (remaining <= 0) continue;
    
                    activeNoteEndTimes.put(note, logicalNow + remaining);
                    activeNoteStartTimes.put(note, logicalNow);  // resume from now later
    
                    ToneGenerator.stopTone(note);
                    JButton btn = keyButtons.get(note);
                    if (btn != null) {
                        btn.setBackground(note.contains("#") ? java.awt.Color.BLACK : java.awt.Color.WHITE);
                    }
                }
    
            } else {
                long resumeTime = System.currentTimeMillis();
                totalPausedTime += (resumeTime - pauseStartTime);
                pauseStartTime = 0;
                SwingUtilities.invokeLater(() -> playResumeBtn.setText("⏸"));
    
                // === Resume all paused notes ===
                long logicalNow = resumeTime - playbackStart.get() - totalPausedTime;
    
                for (String note : new HashSet<>(activePlaybackNotes)) {
                    long end = activeNoteEndTimes.getOrDefault(note, logicalNow + 1);
                    long remaining = end - logicalNow;
    
                    if (remaining <= 0) {
                        activePlaybackNotes.remove(note);
                        continue;
                    }
    
                    String timbre = activeNoteTimbres.getOrDefault(note, "sine");
                    double freq = whiteKeys.getOrDefault(note, blackKeys.getOrDefault(note, -1.0));
    
                    if (freq > 0) {
                        ToneGenerator.playToneContinuous(freq, note, timbre);
                        JButton btn = keyButtons.get(note);
                        if (btn != null) {
                            btn.setBackground(java.awt.Color.YELLOW);
                        }
    
                        new Thread(() -> {
                            try {
                                Thread.sleep(remaining);
                            } catch (InterruptedException ignored) {}
    
                            stopNow(note);
                        }).start();
                    }
                }
    
                playbackLock.notifyAll();
            }
        }
    }

    public void stop() {
        stopRequested = true;
        synchronized (playbackLock) {
            playbackLock.notifyAll();
        }
    }

    private void runPlayback(List<String[]> events) {
        boolean[] played = new boolean[events.size()];

        long totalDuration = events.stream()
                .mapToLong(e -> Long.parseLong(e[2]))
                .max().orElse(0);

        while (true) {
            if (stopRequested) break;

            long logicalTime = System.currentTimeMillis() - playbackStart.get() - totalPausedTime;

            synchronized (playbackLock) {
                if (isPaused) {
                    try {
                        playbackLock.wait();
                    } catch (InterruptedException ignored) {}
                    continue;
                }
            }

            for (int i = 0; i < events.size(); i++) {
                if (played[i]) continue;

                String[] evt = events.get(i);
                String note = evt[0];
                long start = Long.parseLong(evt[1]);
                long end = Long.parseLong(evt[2]);
                String timbre = evt[3];

                if (logicalTime >= start) {
                    played[i] = true;

                    double freq = whiteKeys.getOrDefault(note, blackKeys.getOrDefault(note, -1.0));
                    if (freq > 0) {
                        ToneGenerator.playToneContinuous(freq, note, timbre);
                        activePlaybackNotes.add(note);
                        activeNoteStartTimes.put(note, logicalTime);
                        activeNoteEndTimes.put(note, end);
                        activeNoteTimbres.put(note, timbre);

                        JButton btn = keyButtons.get(note);
                        if (btn != null) SwingUtilities.invokeLater(() -> btn.setBackground(java.awt.Color.YELLOW));

                        long safeSleep = end - logicalTime;
                        if (safeSleep > 0) {
                            new Thread(() -> stopAfterDelay(note, safeSleep)).start();
                        } else {
                            stopNow(note);
                        }
                    }
                }
            }

            int percent = (int) (100.0 * logicalTime / totalDuration);
            SwingUtilities.invokeLater(() -> playbackBar.setValue(Math.min(percent, 100)));

            if (logicalTime >= totalDuration && activePlaybackNotes.isEmpty()) break;

            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {}
        }

        SwingUtilities.invokeLater(() -> {
            playbackBar.setValue(0);
            playResumeBtn.setText("▶");
        });
    }

    private void stopAfterDelay(String note, long delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ignored) {}
        stopNow(note);
    }

    private void stopNow(String note) {
        ToneGenerator.stopTone(note);
        activePlaybackNotes.remove(note);
        activeNoteEndTimes.remove(note);
        activeNoteStartTimes.remove(note);
        activeNoteTimbres.remove(note);
        JButton btn = keyButtons.get(note);
        if (btn != null) SwingUtilities.invokeLater(() ->
                btn.setBackground(note.contains("#") ? java.awt.Color.BLACK : java.awt.Color.WHITE));
    }
}