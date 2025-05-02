import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.swing.*;

public class PianoApp {
    public static final java.util.Map<String, Double> WHITE_KEYS = new java.util.LinkedHashMap<>();
    public static final java.util.Map<String, Double> BLACK_KEYS = new java.util.LinkedHashMap<>();
    static final java.util.List<String[]> rawEvents = new java.util.ArrayList<>();
    private static final java.util.Map<String, Long> activeNotes = new java.util.HashMap<>();

    private static PlaybackManager playbackManager;
    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;
    private static ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    static boolean isRecording = false;
    static long recordingStartTime;
    private static String username;
    private static JCheckBox autoChordCheck;
    private static JComboBox<String> chordTypeSelector;
    private static JTextArea chatArea;
    private static JTextField chatInput;
    private static java.util.List<String[]> currentPlaybackEvents = new java.util.ArrayList<>();
    private static JProgressBar playbackBar;
    private static JButton playResumeBtn;
    private static JLabel currentNoteLabel;
    private static JLabel currentOctaveLabel;
    private static KeyboardManager keyboardManager;

    public static final java.util.Map<String, JButton> keyButtons = new java.util.HashMap<>();
    public static final java.util.Map<String, Integer> pressCount = new java.util.concurrent.ConcurrentHashMap<>();

    public static String TIMBRE = "sine";

    static {
        WHITE_KEYS.put("C4", 261.63);  WHITE_KEYS.put("D4", 293.66);  WHITE_KEYS.put("E4", 329.63);
        WHITE_KEYS.put("F4", 349.23);  WHITE_KEYS.put("G4", 392.00);  WHITE_KEYS.put("A4", 440.00);  WHITE_KEYS.put("B4", 493.88);
        WHITE_KEYS.put("C5", 523.25);  WHITE_KEYS.put("D5", 587.33);  WHITE_KEYS.put("E5", 659.25);
        WHITE_KEYS.put("F5", 698.46);  WHITE_KEYS.put("G5", 783.99);  WHITE_KEYS.put("A5", 880.00);  WHITE_KEYS.put("B5", 987.77);
        WHITE_KEYS.put("C6", 1046.50); WHITE_KEYS.put("D6", 1174.66); WHITE_KEYS.put("E6", 1318.51);
        WHITE_KEYS.put("F6", 1396.91); WHITE_KEYS.put("G6", 1567.98); WHITE_KEYS.put("A6", 1760.00); WHITE_KEYS.put("B6", 1975.53);
        WHITE_KEYS.put("C7", 2093.00);

        BLACK_KEYS.put("C#4", 277.18); BLACK_KEYS.put("D#4", 311.13); BLACK_KEYS.put("F#4", 370.00);
        BLACK_KEYS.put("G#4", 415.30); BLACK_KEYS.put("A#4", 466.16);
        BLACK_KEYS.put("C#5", 554.37); BLACK_KEYS.put("D#5", 622.25); BLACK_KEYS.put("F#5", 739.99);
        BLACK_KEYS.put("G#5", 830.61); BLACK_KEYS.put("A#5", 932.33);
        BLACK_KEYS.put("C#6", 1108.73); BLACK_KEYS.put("D#6", 1244.51); BLACK_KEYS.put("F#6", 1479.98);
        BLACK_KEYS.put("G#6", 1661.22); BLACK_KEYS.put("A#6", 1864.66);
    }



    public static void main(String[] args) throws IOException {
        String serverIP = JOptionPane.showInputDialog("Enter server IP:", "localhost");
        String portStr = JOptionPane.showInputDialog("Enter port:", "5190");
        username = JOptionPane.showInputDialog("Enter your username:");
    
        try {
            socket = new Socket(serverIP, Integer.parseInt(portStr));
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println(username);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(null, "Invalid port number. Please enter a valid integer.");
            System.exit(1);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Failed to connect to the server. Please check the IP and port.");
            System.exit(1);
        }
    
        ToneGenerator.loadPianoSamples();
    
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(WHITE_KEYS.keySet());
        allKeys.addAll(BLACK_KEYS.keySet());
        ToneGenerator.initializeKeys(allKeys);
    
        int whiteKeyWidth = 60;
        int numberOfWhiteKeys = WHITE_KEYS.size();
        int pianoWidth = whiteKeyWidth * numberOfWhiteKeys;
        int pianoHeight = 300;
    
        JFrame frame = new JFrame("Cooperating Piano");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.setResizable(false);
    
        JPanel topPanel = new JPanel(new BorderLayout());
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.setPreferredSize(new Dimension((int)(pianoWidth * 2.0 / 3), 300));
    
        JPanel volumePanel = new JPanel();
        volumePanel.setBorder(BorderFactory.createTitledBorder("Vol"));
        volumePanel.setLayout(new BoxLayout(volumePanel, BoxLayout.Y_AXIS));
        JLabel volumeLabel = new JLabel("", SwingConstants.CENTER);
        JSlider volumeSlider = new JSlider(JSlider.VERTICAL, 0, 100, 100);
        volumeSlider.setMajorTickSpacing(25);
        volumeSlider.setPaintTicks(true);
        volumeSlider.setPaintLabels(true);
        volumeSlider.setPreferredSize(new Dimension(50, 200));
        volumeSlider.addChangeListener(e -> {
            double volume = volumeSlider.getValue() / 100.0;
            ToneGenerator.setGlobalVolume(volume);
        });
        volumePanel.add(volumeLabel);
        volumePanel.add(volumeSlider);
    
        JPanel functionGroupPanel = new JPanel();
        functionGroupPanel.setLayout(new BoxLayout(functionGroupPanel, BoxLayout.Y_AXIS));
    
        // Recording & Playback
        JPanel recordPanel = new JPanel(new GridLayout(2, 3, 5, 5));
        recordPanel.setBorder(BorderFactory.createTitledBorder("Recording & Playback"));
        JButton recordBtn = new JButton("🎙 Start Recording");
        JButton stopBtn = new JButton("⏹ Stop Recording");
        JButton saveBtn = new JButton("💾 Save Recording");
        JButton loadBtn = new JButton("📂 Load & Play");
        JButton resetBtn = new JButton("🔄 Reset");
        playResumeBtn = new JButton("▶");
        playbackBar = new JProgressBar(0, 100);
        playbackBar.setStringPainted(false);
        playbackManager = new PlaybackManager(WHITE_KEYS, BLACK_KEYS, keyButtons, playbackBar, playResumeBtn);
        stopBtn.setEnabled(false);
    
        recordPanel.add(recordBtn);
        recordPanel.add(stopBtn);
        recordPanel.add(saveBtn);
        recordPanel.add(loadBtn);
    
        JPanel playAndBarPanel = new JPanel(new BorderLayout());
        playAndBarPanel.add(playResumeBtn, BorderLayout.WEST);
        playAndBarPanel.add(playbackBar, BorderLayout.CENTER);
        recordPanel.add(playAndBarPanel);
    
        recordPanel.add(resetBtn);
    
        // Timbre & Chord
        JPanel leftPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        JLabel timbreLabel = new JLabel("Timbre:");
        JComboBox<String> timbreSelector = new JComboBox<>(new String[]{"sine", "square", "triangle", "sawtooth", "piano"});
        timbreSelector.setSelectedItem(TIMBRE);
        timbreSelector.addActionListener(e -> TIMBRE = (String) timbreSelector.getSelectedItem());
        autoChordCheck = new JCheckBox("Auto Chord");
        chordTypeSelector = new JComboBox<>(new String[]{"Major", "Minor", "Diminished", "Octave"});
        leftPanel.add(timbreLabel);
        leftPanel.add(timbreSelector);
        leftPanel.add(autoChordCheck);
        leftPanel.add(chordTypeSelector);
        keyboardManager = new KeyboardManager(4, 7, 5, autoChordCheck, chordTypeSelector);

        // Note Indicator
        JPanel rightPanel = new JPanel(new GridLayout(2, 1));  // change to GridLayout for 2 labels

        // Current Note
        currentNoteLabel = new JLabel("None", SwingConstants.CENTER);
        JPanel notePanel = new JPanel(new BorderLayout());
        notePanel.setBorder(BorderFactory.createTitledBorder("Current Note"));
        notePanel.add(currentNoteLabel, BorderLayout.CENTER);

        // Current Octave
        currentOctaveLabel = new JLabel("5", SwingConstants.CENTER);  // default to starting octave (e.g., 5)
        JPanel octavePanel = new JPanel(new BorderLayout());
        octavePanel.setBorder(BorderFactory.createTitledBorder("Current Keyboard Octave"));
        octavePanel.add(currentOctaveLabel, BorderLayout.CENTER);

        rightPanel.add(notePanel);
        rightPanel.add(octavePanel);

        rightPanel.setPreferredSize(new Dimension(200, 100));  // adjust height if needed

        JPanel optionsContainer = new JPanel(new GridLayout(1, 3));
        optionsContainer.setBorder(BorderFactory.createTitledBorder("Play Setting"));

        optionsContainer.add(leftPanel);            // timbre & chord
        optionsContainer.add(rightPanel);           // current note
        optionsContainer.add(createMetronomePanel());  // metronome
    

        functionGroupPanel.add(recordPanel);
        functionGroupPanel.add(optionsContainer);


        controlPanel.add(volumePanel, BorderLayout.WEST);
        controlPanel.add(functionGroupPanel, BorderLayout.CENTER);
    
        // Chat panel
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setPreferredSize(new Dimension(pianoWidth / 3, 300));
        chatArea = new JTextArea(8, 30);
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        JPanel inputPanel = new JPanel(new BorderLayout());
        chatInput = new JTextField();
        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendChat());
        chatInput.addActionListener(e -> sendChat());
        inputPanel.add(chatInput, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        chatPanel.add(scrollPane, BorderLayout.CENTER);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);
    
        topPanel.add(controlPanel, BorderLayout.CENTER);
        topPanel.add(chatPanel, BorderLayout.EAST);
    
        // Piano panel
        JLayeredPane layeredPane = createPiano();
        layeredPane.setPreferredSize(new Dimension(pianoWidth, pianoHeight));
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(layeredPane, BorderLayout.CENTER);
        frame.pack();
    
        int topPanelHeight = topPanel.getPreferredSize().height;
        frame.setSize(pianoWidth, pianoHeight + topPanelHeight);
        frame.setVisible(true);

        // Set up key bindings on the frame's root pane
        InputMap inputMap = frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = frame.getRootPane().getActionMap();

        // Bind all note keys
        for (int keyCode : KeyboardManager.getSupportedKeyCodes()) {
            // Press action
            inputMap.put(KeyStroke.getKeyStroke(keyCode, 0, false), "press_" + keyCode);
            actionMap.put("press_" + keyCode, new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    keyboardManager.handleKeyPress(new KeyEvent(frame, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, keyCode, (char) keyCode));
                }
            });

            // Release action
            inputMap.put(KeyStroke.getKeyStroke(keyCode, 0, true), "release_" + keyCode);
            actionMap.put("release_" + keyCode, new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    keyboardManager.handleKeyRelease(new KeyEvent(frame, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, keyCode, (char) keyCode));
                }
            });
        }

        // Bind octave switch keys: 4/5/6/7
        for (int keyCode : new int[]{KeyEvent.VK_4, KeyEvent.VK_5, KeyEvent.VK_6, KeyEvent.VK_7}) {
            inputMap.put(KeyStroke.getKeyStroke(keyCode, 0, false), "press_" + keyCode);
            actionMap.put("press_" + keyCode, new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    keyboardManager.handleKeyPress(new KeyEvent(frame, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, keyCode, (char) keyCode));
                }
            });
        }
    
        // button behaviors
        recordBtn.addActionListener(e -> {
            isRecording = true;
            rawEvents.clear();
            activeNotes.clear();
            recordingStartTime = System.currentTimeMillis();
            recordBtn.setEnabled(false);
            stopBtn.setEnabled(true);
        });
    
        stopBtn.addActionListener(e -> {
            isRecording = false;
            recordBtn.setEnabled(true);
            stopBtn.setEnabled(false);
            currentPlaybackEvents = convertRawEventsToPlaybackFormat(rawEvents);
            SwingUtilities.invokeLater(() -> {
                playbackBar.setValue(0);
                playResumeBtn.setText("▶");
                playResumeBtn.setEnabled(true);
            });
        });
    
        saveBtn.addActionListener(e -> saveRecording());
        loadBtn.addActionListener(e -> loadAndPlay());
        resetBtn.addActionListener(e -> resetAllNotes());
    
        playResumeBtn.addActionListener(e -> {
            if (!currentPlaybackEvents.isEmpty()) {
                if (playbackBar.getValue() > 0 && playbackBar.getValue() < 100) {
                    playbackManager.togglePause();
                } else {
                    playbackManager.play(currentPlaybackEvents);
                    playResumeBtn.setText("⏸");
                }
            } else {
                JOptionPane.showMessageDialog(null, "No recorded or loaded data to play.");
            }
        });
    
        networkExecutor.submit(PianoApp::listenForMessages);
    }

    private static JLayeredPane createPiano() {
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setPreferredSize(new Dimension(WHITE_KEYS.size() * 60, 300));
    
        Supplier<Long> recordingStartTimeSupplier = () -> recordingStartTime;
        Supplier<Boolean> isAutoChordEnabled = () -> autoChordCheck.isSelected();
        Supplier<String> chordTypeSupplier = () -> (String) chordTypeSelector.getSelectedItem();
    
        int whiteKeyIndex = 0;
        Map<String, Integer> whiteKeyPositions = new HashMap<>();
        List<String> whiteNoteList = new ArrayList<>(WHITE_KEYS.keySet());
    
        // White keys
        for (String note : whiteNoteList) {
            double freq = WHITE_KEYS.get(note);
            int x = whiteKeyIndex * 60;
            whiteKeyPositions.put(note, whiteKeyIndex);
            JButton key = KeyFactory.createKey(
                note, freq, false,
                x, 0, 60, 300,
                keyButtons, pressCount, rawEvents, activeNotes,
                recordingStartTimeSupplier,
                () -> isRecording,
                () -> TIMBRE,
                PianoApp::sendMessage,
                ToneGenerator::stopTone,
                n -> {
                    ToneGenerator.playToneContinuous(freq, n, TIMBRE);
                    updateCurrentNoteLabel(n);
                },
                isAutoChordEnabled,
                chordTypeSupplier
            );
            layeredPane.add(key, JLayeredPane.DEFAULT_LAYER);
            whiteKeyIndex++;
        }
    
        // black key
        for (String blackNote : BLACK_KEYS.keySet()) {
            double freq = BLACK_KEYS.get(blackNote);
    

            String baseWhite = blackNote.replace("#", "");
            int whiteIndex = -1;
            for (int i = 0; i < whiteNoteList.size(); i++) {
                if (whiteNoteList.get(i).startsWith(baseWhite)) {
                    whiteIndex = i;
                    break;
                }
            }
    
            if (whiteIndex == -1 || whiteIndex + 1 >= whiteNoteList.size()) continue;
    
            int bx = (whiteIndex + 1) * 60 - 20;
    
            JButton key = KeyFactory.createKey(
                blackNote, freq, true,
                bx, 0, 40, 180,
                keyButtons, pressCount, rawEvents, activeNotes,
                recordingStartTimeSupplier,
                () -> isRecording,
                () -> TIMBRE,
                PianoApp::sendMessage,
                ToneGenerator::stopTone,
                n -> {
                    ToneGenerator.playToneContinuous(freq, n, TIMBRE);
                    updateCurrentNoteLabel(n);
                },
                isAutoChordEnabled,
                chordTypeSupplier
            );
            layeredPane.add(key, JLayeredPane.PALETTE_LAYER);
        }
    
        return layeredPane;
    }

    private static void saveRecording() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            try (PrintWriter writer = new PrintWriter(chooser.getSelectedFile())) {
                writer.println("note,startTime,endTime,timbre");
                List<String[]> converted = convertRawEventsToPlaybackFormat(rawEvents);
                for (String[] row : converted) {
                    writer.println(String.join(",", row));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private static void loadAndPlay() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            java.util.List<String[]> loaded = new java.util.ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String header = reader.readLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length == 4) loaded.add(parts);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            currentPlaybackEvents = loaded;
            JOptionPane.showMessageDialog(null, "File loaded successfully!", "Message", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    


    private static void resetAllNotes() {
        // Stop ALL tones
        ToneGenerator.stopAllTones();
    
        // Reset UI colors
        for (var entry : keyButtons.entrySet()) {
            String note = entry.getKey();
            JButton key = entry.getValue();
            key.setBackground(note.contains("#") ? Color.BLACK : Color.WHITE);
        }
    
        pressCount.clear();
    }

    static void sendMessage(String msg) {
        networkExecutor.submit(() -> {
            if (out != null) out.println("MUSIC," + msg);
        });
    }

    private static void sendChat() {
        String text = chatInput.getText().trim();
        if (!text.isEmpty()) {
            out.println("CHAT," + text);
            chatInput.setText("");
        }
    }

    private static void listenForMessages() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                int firstComma = line.indexOf(',');
                if (firstComma != -1) {
                    String category = line.substring(0, firstComma);
                    String content = line.substring(firstComma + 1);

                    if (category.equals("MUSIC")) {
                        handleMusicMessage(content);
                    } else if (category.equals("CHAT")) {
                        SwingUtilities.invokeLater(() -> {
                            chatArea.append(content + "\n");
                            chatArea.setCaretPosition(chatArea.getDocument().getLength());
                        });
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Disconnected from server.");
        }
    }

    private static void handleMusicMessage(String content) {
        String[] parts = content.split(",");
        if (parts.length >= 3) {
            String type = parts[0];
            String note = parts[1];
            String timbre = parts[2];
    
            double freq = WHITE_KEYS.getOrDefault(note, BLACK_KEYS.getOrDefault(note, -1.0));
            if (freq > 0) {
                if (type.equals("NOTE_ON")) {
                    int count = pressCount.getOrDefault(note, 0);
                    if (count == 0) {
                        ToneGenerator.playToneContinuous(freq, note, timbre);
                    }
                    pressCount.put(note, count + 1);
                    JButton key = keyButtons.get(note);
                    if (key != null) {
                        SwingUtilities.invokeLater(() -> key.setBackground(Color.YELLOW));
                    }
                } else if (type.equals("NOTE_OFF")) {
                    int count = pressCount.getOrDefault(note, 1) - 1;
                    if (count <= 0) {
                        pressCount.remove(note);
                        ToneGenerator.stopTone(note);
                    } else {
                        pressCount.put(note, count);
                    }
                    JButton key = keyButtons.get(note);
                    if (key != null) {
                        SwingUtilities.invokeLater(() -> key.setBackground(note.contains("#") ? Color.BLACK : Color.WHITE));
                    }
                }
            }
        }
    }



    private static List<String[]> convertRawEventsToPlaybackFormat(List<String[]> rawEvents) {
        List<String[]> result = new ArrayList<>();
        Map<String, String[]> activeMap = new HashMap<>();
    
        for (String[] evt : rawEvents) {
            if (evt[0].equals("NOTE_ON")) {
                activeMap.put(evt[1], evt);
            } else if (evt[0].equals("NOTE_OFF") && activeMap.containsKey(evt[1])) {
                String[] start = activeMap.remove(evt[1]);
                result.add(new String[] {
                    evt[1],       // note
                    start[2],     // start time
                    evt[2],       // end time
                    evt[3]        // timbre
                });
            }
        }
    
        return result;
    }

    public static void updateCurrentNoteLabel(String note) {
        SwingUtilities.invokeLater(() -> {
            if (currentNoteLabel != null) {
                currentNoteLabel.setText(note);
                // System.out.println("Updating note label: " + note);
            }
        });
    }

    
    public static JPanel createMetronomePanel() {
        JPanel metronomePanel = new JPanel(new BorderLayout());
        metronomePanel.setBorder(BorderFactory.createTitledBorder("Metronome"));
    
        JLabel bpmLabel = new JLabel("BPM:");
        JSpinner bpmSpinner = new JSpinner(new SpinnerNumberModel(120, 30, 300, 1));  // default 120 BPM
        JButton startStopBtn = new JButton("Start");
    
        JLabel beatIndicator = new JLabel("●", SwingConstants.CENTER);
        beatIndicator.setFont(new Font("SansSerif", Font.BOLD, 30));
        beatIndicator.setForeground(Color.GRAY);
    
        JPanel controlPanel = new JPanel();
        controlPanel.add(bpmLabel);
        controlPanel.add(bpmSpinner);
        controlPanel.add(startStopBtn);
    
        metronomePanel.add(controlPanel, BorderLayout.NORTH);
        metronomePanel.add(beatIndicator, BorderLayout.CENTER);
    
        Timer[] driverTimer = new Timer[1];
        final long[] nextBeatTime = new long[1];
    
        startStopBtn.addActionListener(e -> {
            if (driverTimer[0] == null) {
                int bpm = (Integer) bpmSpinner.getValue();
                nextBeatTime[0] = System.currentTimeMillis();
    
                driverTimer[0] = new Timer(10, ev -> {  // Check every 10ms, change the beep interval
                    long now = System.currentTimeMillis();
                    int currentBpm = (Integer) bpmSpinner.getValue();
                    long currentInterval = 60000 / currentBpm;
    
                    if (now >= nextBeatTime[0]) {
                        beatIndicator.setForeground(Color.RED);
                        new Thread(() -> ToneGenerator.beepSound()).start();
    
                        Timer flashOff = new Timer(100, evt -> beatIndicator.setForeground(Color.GRAY));
                        flashOff.setRepeats(false);
                        flashOff.start();
    
                        nextBeatTime[0] = now + currentInterval;
                    }
                });
                driverTimer[0].start();
                startStopBtn.setText("Stop");
            } else {
                driverTimer[0].stop();
                driverTimer[0] = null;
                beatIndicator.setForeground(Color.GRAY);
                startStopBtn.setText("Start");
            }
        });
    
        return metronomePanel;
    }

    
    public static void updateCurrentOctaveLabel(int octave) {
        SwingUtilities.invokeLater(() -> {
            if (currentOctaveLabel != null) {
                currentOctaveLabel.setText(String.valueOf(octave));
            }
        });
    }

    
}
