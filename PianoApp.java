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
    private static String username;
    private static JCheckBox autoChordCheck;
    private static JComboBox<String> chordTypeSelector;
    private static JTextArea chatArea;
    private static JTextField chatInput;
    private static java.util.List<String[]> currentPlaybackEvents = new java.util.ArrayList<>();
    // private static JProgressBar playbackBar;
    // private static JButton playResumeBtn;
    private static JLabel currentNoteLabel;
    private static JLabel currentOctaveLabel;
    private static KeyboardManager keyboardManager;

    public static final java.util.Map<String, JButton> keyButtons = new java.util.HashMap<>();
    public static final java.util.Map<String, Integer> pressCount = new java.util.concurrent.ConcurrentHashMap<>();

    public static String TIMBRE = "sine";

    static boolean isRecording = false;
    static long recordingStartTime;

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
        // 1) Connect to server
        String serverIP = JOptionPane.showInputDialog("Enter server IP:", "localhost");
        String portStr  = JOptionPane.showInputDialog("Enter port:",    "5190");
        username = JOptionPane.showInputDialog("Enter your username:");
        try {
            socket = new Socket(serverIP, Integer.parseInt(portStr));
            out    = new PrintWriter(socket.getOutputStream(), true);
            in     = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println(username);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(null, "Invalid port number. Please enter a valid integer.");
            System.exit(1);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Failed to connect to the server. Please check the IP and port.");
            System.exit(1);
        }
    
        // 2) Initialize audio & keys
        ToneGenerator.loadPianoSamples();
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(WHITE_KEYS.keySet());
        allKeys.addAll(BLACK_KEYS.keySet());
        ToneGenerator.initializeKeys(allKeys);
    
        // 3) Compute layout sizes
        int whiteKeyWidth   = 60;
        int numberOfWhite   = WHITE_KEYS.size();
        int pianoWidth      = whiteKeyWidth * numberOfWhite;
        int pianoHeight     = 300;
    
        // 4) Frame setup
        JFrame frame = new JFrame("Cooperating Piano");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.setResizable(false);
    
        // 5) Top-level panels
        JPanel topPanel     = new JPanel(new BorderLayout());
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.setPreferredSize(new Dimension((int)(pianoWidth * 2.0/3), 300));
    
        // —— Volume panel (WEST) ——
        JPanel volumePanel = new JPanel();
        volumePanel.setBorder(BorderFactory.createTitledBorder("Vol"));
        volumePanel.setLayout(new BoxLayout(volumePanel, BoxLayout.Y_AXIS));
        JLabel volumeLabel = new JLabel("", SwingConstants.CENTER);
        JSlider volumeSlider = new JSlider(JSlider.VERTICAL, 0, 100, 100);
        volumeSlider.setMajorTickSpacing(25);
        volumeSlider.setPaintTicks(true);
        volumeSlider.setPaintLabels(true);
        volumeSlider.setPreferredSize(new Dimension(50,300));
        volumeSlider.addChangeListener(e -> {
            double vol = volumeSlider.getValue()/100.0;
            ToneGenerator.setGlobalVolume(vol);
        });
        volumePanel.add(volumeLabel);
        volumePanel.add(volumeSlider);
    
        // Measure and compute half-height of volumePanel
        Dimension volDim = volumePanel.getPreferredSize();
        int volH = volDim.height;       // expected 200
        int halfH = volH / 2;           // e.g. 100
    
        // —— Metronome panel (EAST) ——
        Metronome metronome = new Metronome();
        JPanel metroPanel    = metronome.getPanel();
        metroPanel.setPreferredSize(new Dimension(212,200));
        JPanel metroWrapper  = new JPanel(new BorderLayout());
        metroWrapper.setBorder(BorderFactory.createEmptyBorder(0,5,0,0));
        metroWrapper.add(metroPanel, BorderLayout.CENTER);
    
        // —— Record & Playback panel —— 
        JPanel recordPanel = new JPanel(new GridBagLayout());
        recordPanel.setBorder(BorderFactory.createTitledBorder("Recording & Playback"));
    
        JButton recordBtn     = new JButton("🎙 Start Recording");
        JButton stopBtn       = new JButton("⏹ Stop Recording");
        JButton saveBtn       = new JButton("💾 Save Recording");
        JButton loadBtn       = new JButton("📂 Load & Play");
        JButton resetBtn      = new JButton("🔄 Reset");
        JButton playResumeBtn = new JButton("▶");
        JProgressBar playbackBar = new JProgressBar(0,100);
        playbackManager = new PlaybackManager(
            WHITE_KEYS, BLACK_KEYS, keyButtons,
            playbackBar, playResumeBtn
        );
        playbackBar.setStringPainted(false);
        
    
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(3,5,3,5);
        gbc.fill    = GridBagConstraints.BOTH;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.ipady   = 20; 
    
        // First row
        gbc.gridy = 0;
        gbc.gridx = 0; recordPanel.add(recordBtn, gbc);
        gbc.gridx = 1; recordPanel.add(stopBtn,   gbc);
        gbc.gridx = 2; recordPanel.add(saveBtn,   gbc);
        gbc.gridx = 3; recordPanel.add(resetBtn,  gbc);
    
        // Second row: load, play/resume, then progress bar spanning two columns
        gbc.gridy     = 1;
        gbc.gridwidth = 1;
        gbc.weightx   = 0.0;
        gbc.gridx     = 0; recordPanel.add(loadBtn,      gbc);
        gbc.gridx     = 1; recordPanel.add(playResumeBtn,gbc);

        // now make the bar span cols 2 & 3
        gbc.gridx     = 2;
        gbc.gridwidth = 2;
        gbc.weightx   = 1.0;  // let that two-cell span grow
        recordPanel.add(playbackBar, gbc);

        // reset for future adds
        gbc.gridwidth = 1;
        gbc.weightx   = 0.0;
    
        // Enforce half-height on recordPanel
        recordPanel.setPreferredSize(new Dimension(recordPanel.getPreferredSize().width, halfH));
        recordPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, halfH));
    
        // —— Play Setting panels (Timbre/Chord & Note/Octave) —— 
        JPanel leftPanel  = new JPanel(new GridLayout(2,2,5,5));
        JLabel timbreLabel = new JLabel("Timbre:");
        JComboBox<String> timbreSelector = new JComboBox<>(new String[]{"sine","square","triangle","sawtooth","piano"});
        timbreSelector.setSelectedItem(TIMBRE);
        timbreSelector.addActionListener(e -> TIMBRE = (String)timbreSelector.getSelectedItem());
        autoChordCheck    = new JCheckBox("Auto Chord");
        chordTypeSelector = new JComboBox<>(new String[]{"Major","Minor","Diminished","Octave"});
        leftPanel.add(timbreLabel);
        leftPanel.add(timbreSelector);
        leftPanel.add(autoChordCheck);
        leftPanel.add(chordTypeSelector);
        keyboardManager = new KeyboardManager(4,7,5, autoChordCheck, chordTypeSelector);
    
        JPanel rightPanel = new JPanel(new GridLayout(2,1));
        currentNoteLabel   = new JLabel("None", SwingConstants.CENTER);
        JPanel notePanel   = new JPanel(new BorderLayout());
        notePanel.setBorder(BorderFactory.createTitledBorder("Current Note"));
        notePanel.add(currentNoteLabel, BorderLayout.CENTER);
        currentOctaveLabel = new JLabel("5", SwingConstants.CENTER);
        JPanel octavePanel = new JPanel(new BorderLayout());
        octavePanel.setBorder(BorderFactory.createTitledBorder("Current Keyboard Octave"));
        octavePanel.add(currentOctaveLabel, BorderLayout.CENTER);
        rightPanel.add(notePanel);
        rightPanel.add(octavePanel);
    
        JPanel optionsContainer = new JPanel(new GridLayout(1,2,5,5));
        optionsContainer.setBorder(BorderFactory.createTitledBorder("Play Setting"));
        optionsContainer.add(leftPanel);
        optionsContainer.add(rightPanel);
    
        // Enforce half-height on optionsContainer
        optionsContainer.setPreferredSize(new Dimension(optionsContainer.getPreferredSize().width, halfH));
        optionsContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, halfH));
    
        JPanel functionGroupPanel = new JPanel();
        functionGroupPanel.setLayout(new BoxLayout(functionGroupPanel, BoxLayout.Y_AXIS));
        functionGroupPanel.add(recordPanel);
        functionGroupPanel.add(optionsContainer);
    
        // —— Assemble controlPanel —— 
        controlPanel.add(volumePanel,        BorderLayout.WEST);
        controlPanel.add(functionGroupPanel, BorderLayout.CENTER);
        controlPanel.add(metroWrapper,       BorderLayout.EAST);
    
        // —— Chat panel —— 
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setPreferredSize(new Dimension(pianoWidth/3,300));
        chatArea = new JTextArea(8,30);
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        JPanel inputPanel = new JPanel(new BorderLayout());
        chatInput = new JTextField();
        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendChat());
        chatInput.addActionListener(e -> sendChat());
        inputPanel.add(chatInput,BorderLayout.CENTER);
        inputPanel.add(sendButton,BorderLayout.EAST);
        chatPanel.add(scrollPane,BorderLayout.CENTER);
        chatPanel.add(inputPanel,BorderLayout.SOUTH);
    
        topPanel.add(controlPanel,BorderLayout.CENTER);
        topPanel.add(chatPanel,   BorderLayout.EAST);
    
        // —— Piano panel —— 
        JLayeredPane layeredPane = createPiano();
        layeredPane.setPreferredSize(new Dimension(pianoWidth,pianoHeight));
        frame.add(topPanel,    BorderLayout.NORTH);
        frame.add(layeredPane, BorderLayout.CENTER);
        frame.pack();
        frame.setSize(pianoWidth, pianoHeight + topPanel.getPreferredSize().height);
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
                    if (chatInput.isFocusOwner()) {
                        return;  // Skip if the chat box is focused
                    }
                    keyboardManager.handleKeyPress(new KeyEvent(frame, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, keyCode, (char) keyCode));
                }
            });

            // Release action
            inputMap.put(KeyStroke.getKeyStroke(keyCode, 0, true), "release_" + keyCode);
            actionMap.put("release_" + keyCode, new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    if (chatInput.isFocusOwner()) {
                        return;  // Skip if the chat box is focused
                    }
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
                    if (chatInput.isFocusOwner()) {
                        return;  // Skip if the chat box is focused
                    }
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
        // System.out.println("[DEBUG] Directly sending to server: MUSIC," + msg);
        if (out != null) out.println("MUSIC," + msg);
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
            while (true) {
                line = in.readLine();
                if (line == null) {
                    // disconnect and throw exception
                    throw new IOException("Server closed the connection");
                }
    
                int firstComma = line.indexOf(',');
                if (firstComma != -1) {
                    String category = line.substring(0, firstComma);
                    String content  = line.substring(firstComma + 1);
    
                    if (category.equals("MUSIC") ||
                        category.equals("NOTE_ON") ||
                        category.equals("NOTE_OFF")) {
                        handleMusicMessage(line);
                    } else if (category.equals("CHAT")) {
                        SwingUtilities.invokeLater(() -> {
                            chatArea.append(content + "\n");
                            chatArea.setCaretPosition(chatArea.getDocument().getLength());
                        });
                    }
                }
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(
                    null,
                    "disconnected from the server:" + e.getMessage(),
                    "Connection Error",
                    JOptionPane.ERROR_MESSAGE
                );
                // disable chat input
                chatInput.setEditable(false);
            });
            System.out.println("Disconnected from server:  " + e.getMessage());
        }
    }

    private static void handleMusicMessage(String message) {
        // System.out.println("[CLIENT HANDLE] Message passed in: " + message);
        String[] parts = message.split(",");
    
        int typeIndex = 0;  // by default assume format: NOTE_ON,C5,sine
        if (parts[0].equals("MUSIC")) {
            // Shift: MUSIC,NOTE_ON,C5,sine
            typeIndex = 1;
        }
    
        if (parts.length - typeIndex >= 3) {
            String type = parts[typeIndex];
            String note = parts[typeIndex + 1];
            String timbre = parts[typeIndex + 2];
            // System.out.println("[CLIENT HANDLE] type=" + type + ", note=" + note + ", timbre=" + timbre);
    
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

    
    public static void updateCurrentOctaveLabel(int octave) {
        SwingUtilities.invokeLater(() -> {
            if (currentOctaveLabel != null) {
                currentOctaveLabel.setText(String.valueOf(octave));
            }
        });
    }

    
}
