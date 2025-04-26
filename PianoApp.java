import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.UUID;
import java.util.concurrent.*;
import javax.swing.*;

public class PianoApp {
    private static final java.util.Map<String, Double> WHITE_KEYS = new java.util.LinkedHashMap<>();
    private static final java.util.Map<String, Double> BLACK_KEYS = new java.util.LinkedHashMap<>();
    private static final java.util.Map<String, JButton> keyButtons = new java.util.HashMap<>();
    private static final java.util.List<String[]> rawEvents = new java.util.ArrayList<>();
    private static final java.util.Map<String, Long> activeNotes = new java.util.HashMap<>();
    private static final java.util.Map<String, Integer> pressCount = new java.util.concurrent.ConcurrentHashMap<>();
    private static final String CLIENT_ID = UUID.randomUUID().toString();

    private static String TIMBRE = "sine";
    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;
    private static ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private static boolean isRecording = false;
    private static long recordingStartTime;
    private static String username;

    private static JTextArea chatArea;
    private static JTextField chatInput;

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

        socket = new Socket(serverIP, Integer.parseInt(portStr));
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out.println(username);
        new Thread(PianoApp::listenForMessages).start();


    // Constants for layout
    int whiteKeyWidth = 60;
    int numberOfWhiteKeys = WHITE_KEYS.size(); // Dynamically from your map
    int pianoWidth = whiteKeyWidth * numberOfWhiteKeys;
    int pianoHeight = 300;  // From your createPiano()

    // Create frame
    JFrame frame = new JFrame("Cooperating Piano 🎹");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setLayout(new BorderLayout());
    frame.setResizable(false);

    // Create top panel (Control buttons + Chat)
    JPanel topPanel = new JPanel(new GridLayout(1, 2));

    // --- Control Panel ---
    JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JButton recordBtn = new JButton("🎙 Start Recording");
    JButton stopBtn = new JButton("⏹ Stop Recording");
    JButton saveBtn = new JButton("💾 Save Recording");
    JButton loadBtn = new JButton("📂 Load & Play");
    JButton changeTimbreBtn = new JButton("Timbre Selection");

    controlPanel.add(recordBtn);
    controlPanel.add(stopBtn);
    controlPanel.add(saveBtn);
    controlPanel.add(loadBtn);
    controlPanel.add(changeTimbreBtn);

    stopBtn.setEnabled(false);

    // Control panel actions
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
    });

    saveBtn.addActionListener(e -> saveRecording());
    loadBtn.addActionListener(e -> loadAndPlay());
    changeTimbreBtn.addActionListener(e -> changeTimbre());

    // --- Chat Panel ---
    JPanel chatPanel = new JPanel(new BorderLayout());
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

    topPanel.add(controlPanel);
    topPanel.add(chatPanel);

    // --- Piano Panel ---
    JLayeredPane layeredPane = createPiano();  // Your own method
    layeredPane.setPreferredSize(new Dimension(pianoWidth, pianoHeight));
    frame.add(topPanel, BorderLayout.NORTH);
    frame.add(layeredPane, BorderLayout.CENTER);
    frame.pack();
    int topPanelHeight = topPanel.getPreferredSize().height;
    frame.setSize(pianoWidth, pianoHeight + topPanelHeight);

    
    frame.setVisible(true);
    }

    private static JButton createKey(String note, double freq, boolean isBlack, int x, int y, int w, int h) {
        JButton key = new JButton();
        key.setBounds(x, y, w, h);
        key.setBackground(isBlack ? Color.BLACK : Color.WHITE);
        key.setOpaque(true);
        key.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        keyButtons.put(note, key);

        key.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                int count = pressCount.getOrDefault(note, 0);
                if (count == 0) {
                    ToneGenerator.playToneContinuous(freq, note, TIMBRE);
                }
                pressCount.put(note, count + 1);
                sendMessage("NOTE_ON," + note + "," + TIMBRE);

                long now = System.currentTimeMillis();
                if (isRecording) {
                    long offset = now - recordingStartTime;
                    rawEvents.add(new String[]{"NOTE_ON", note, String.valueOf(offset), TIMBRE});
                    activeNotes.put(note, offset);
                }

                key.setBackground(isBlack ? Color.GRAY : Color.CYAN);
            }

            public void mouseReleased(MouseEvent e) {
                int count = pressCount.getOrDefault(note, 1) - 1;
                if (count <= 0) {
                    pressCount.remove(note);
                    ToneGenerator.stopTone(note);
                } else {
                    pressCount.put(note, count);
                }

                sendMessage("NOTE_OFF," + note + "," + TIMBRE);

                long now = System.currentTimeMillis();
                if (isRecording && activeNotes.containsKey(note)) {
                    long offset = now - recordingStartTime;
                    rawEvents.add(new String[]{"NOTE_OFF", note, String.valueOf(offset), TIMBRE});
                    activeNotes.remove(note);
                }

                key.setBackground(isBlack ? Color.BLACK : Color.WHITE);
            }
        });

        return key;
    }

    private static JLayeredPane createPiano() {
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setPreferredSize(new Dimension(WHITE_KEYS.size() * 60, 300));

        int x = 0;
        for (String note : WHITE_KEYS.keySet()) {
            double freq = WHITE_KEYS.get(note);
            JButton key = createKey(note, freq, false, x, 0, 60, 300);
            layeredPane.add(key, JLayeredPane.DEFAULT_LAYER);
            x += 60;
        }

        java.util.Map<String, Integer> blackOffsets = java.util.Map.ofEntries(
            java.util.Map.entry("C#4", 0), java.util.Map.entry("D#4", 1), java.util.Map.entry("F#4", 3),
            java.util.Map.entry("G#4", 4), java.util.Map.entry("A#4", 5), java.util.Map.entry("C#5", 7),
            java.util.Map.entry("D#5", 8), java.util.Map.entry("F#5", 10), java.util.Map.entry("G#5", 11),
            java.util.Map.entry("A#5", 12), java.util.Map.entry("C#6", 14), java.util.Map.entry("D#6", 15),
            java.util.Map.entry("F#6", 17), java.util.Map.entry("G#6", 18), java.util.Map.entry("A#6", 19)
        );

        for (var entry : blackOffsets.entrySet()) {
            String note = entry.getKey();
            double freq = BLACK_KEYS.get(note);
            int bx = (entry.getValue() + 1) * 60 - 20;
            JButton key = createKey(note, freq, true, bx, 0, 40, 180);
            layeredPane.add(key, JLayeredPane.PALETTE_LAYER);
        }

        return layeredPane;
    }

    private static void saveRecording() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            try (PrintWriter writer = new PrintWriter(chooser.getSelectedFile())) {
                writer.println("note,startTime,endTime,timbre");
                java.util.Map<String, String[]> noteMap = new java.util.HashMap<>();
                for (String[] evt : rawEvents) {
                    if (evt[0].equals("NOTE_ON")) {
                        noteMap.put(evt[1], evt);
                    } else if (evt[0].equals("NOTE_OFF") && noteMap.containsKey(evt[1])) {
                        String[] start = noteMap.remove(evt[1]);
                        writer.println(evt[1] + "," + start[2] + "," + evt[2] + "," + evt[3]);
                    }
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
            new Thread(() -> playFromFile(file)).start();
        }
    }

    private static void changeTimbre() {
        String[] timbres = {"sine", "square", "sawtooth", "triangle"};
        String selectedTimbre = (String) JOptionPane.showInputDialog(null, "Select Timbre:", "Timbre Selection",
                JOptionPane.PLAIN_MESSAGE, null, timbres, TIMBRE);
        if (selectedTimbre != null) {
            TIMBRE = selectedTimbre;
        }
    }

    private static void sendMessage(String msg) {
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
                String[] parts = line.split(",", 2);
                if (parts.length >= 2) {
                    String category = parts[0];
                    String content = parts[1];
    
                    if (category.equals("MUSIC")) {
                        handleMusicMessage(content);
                    } else if (category.equals("CHAT")) {
                        chatArea.append(content + "\n");
                        chatArea.setCaretPosition(chatArea.getDocument().getLength());
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
                    if (key != null) key.setBackground(Color.YELLOW);
                } else if (type.equals("NOTE_OFF")) {
                    int count = pressCount.getOrDefault(note, 1) - 1;
                    if (count <= 0) {
                        pressCount.remove(note);
                        ToneGenerator.stopTone(note);
                    } else {
                        pressCount.put(note, count);
                    }
                    JButton key = keyButtons.get(note);
                    if (key != null) key.setBackground(note.contains("#") ? Color.BLACK : Color.WHITE);
                }
            }
        }
    }

    private static void playFromFile(File file) {
        java.util.List<String[]> notes = new java.util.ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String header = reader.readLine(); // Skip header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 4) notes.add(parts);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    
        long startTime = System.currentTimeMillis();
        for (String[] entry : notes) {
            String note = entry[0];
            long start = Long.parseLong(entry[1]);
            long end = Long.parseLong(entry[2]);
            String timbre = entry[3];
    
            new Thread(() -> {
                try {
                    long waitBeforeStart = start - (System.currentTimeMillis() - startTime);
                    if (waitBeforeStart > 0) {
                        Thread.sleep(waitBeforeStart);
                    }
                    double freq = WHITE_KEYS.getOrDefault(note, BLACK_KEYS.getOrDefault(note, -1.0));
                    if (freq > 0) {
                        ToneGenerator.playToneContinuous(freq, note, timbre);
                        JButton key = keyButtons.get(note);
                        if (key != null) key.setBackground(Color.YELLOW);
                    }
    
                    long duration = end - start;
                    Thread.sleep(duration);
    
                    ToneGenerator.stopTone(note);
                    JButton key = keyButtons.get(note);
                    if (key != null) key.setBackground(note.contains("#") ? Color.BLACK : Color.WHITE);
                } catch (InterruptedException ignored) {}
            }).start();
        }
    }

}