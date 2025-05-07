## Final Project Report: Collaborative Java Swing Piano Application

### 1. Abstract

This project delivers a networked piano application built in Java that enables two users to play and chat in real time, record the piano session to a file, and replay it with accurate timing. The implementation addresses four core topics from the course requirements—thread concurrency with synchronization, file I/O, networking via sockets, and GUI graphics—in distinct modules to ensure clarity, modularity, and maintainability.

The project repository can be accessed at [Github](https://github.com/zhaodong-liu/Java-Cooperating-Piano.git)

### 2. Introduction

This project application architecture comprises the following components:

**Graphics**: Java Swing-based piano keyboard GUI with Metronome using graphical methods.

**Networking (sockets)**: TCP socket communication for music note events and text messages.

**Thread concurrency**: Multi-threaded management of audio playback, recording, network I/O, metronome, and GUI events.

**File IO**: File-based storage and retrieval of recorded sessions, real piano sample sound loading.

### 3. Implementation Details
#### 3.1 Graphics
##### Java Swing/AWT Foundation
I built the entire UI using the standard Swing toolkit (JFrame, JPanel, JButton, JSpinner, etc.).
##### Vector-Based Drawing with Graphics2D
Cast to Graphics2D to draw a virtual metronome with a shape, simulated pendulum, and a balance weight. 

<br>

<br>

##### Affine Transforms for Animation
I also learned to use AffineTransform (translate + rotate) on the Graphics2D context to handle pendulum rotation about its pivot, rather than manually computing rotated coordinates. Because the metronome rotates fast and this method can achieve a good effect.

#### 3.2 Networking (sockets)
##### Client–Server Architecture
**Server**: a dedicated ServerSocket listening on port 5190; accepts incoming Socket connections and spawns a handler thread for each client.

**Client**: connects via new Socket(host, port), then wraps InputStream/OutputStream with DataInputStream/DataOutputStream for framed, chat and event messages.

**Communication**: All messages are encoded in JSON in two kinds: "MUSIC" and "CHAT". The server relays each incoming event to all other clients to keep GUIs and audio playback in sync.

#### 3.3 Thread Concurrency
##### Playback
For each incoming noteOn event, spawns a new Thread (or submits a task to a fixed‐size ExecutorService) that:

1. Opens the appropriate SourceDataLine.
2. Streams audio buffer until note‐off or release.
3. Closes line.

##### Network Synchronization
Each socket connection uses its thread to read JSON messages and enqueuing them on a thread‐safe queue.
##### Local Synchronization
Shared state (e.g. activePlaybackNotes) stored in ConcurrentHashMap and ConcurrentSkipListSet to avoid explicitly synchronized blocks.

Timers and playback timestamps are also tracked to account for pause/resume delays safely across threads.



#### 3.4 File IO
##### Loading & Saving Recordings
The notes are saved in format: note,startTime,endTime, timbre, which is easy to code and modify outside. RecordingManager logs every note event (including note, timbre and timestamp) to a local file, using BufferedWriter over FileWriter.

On “Save”, flushes buffer and closes stream; on “Load”, reads file line by line with BufferedReader, reconstructs events, and replays them in order.

##### Piano Sample Files
Piano samples (.wav) are loaded at startup and cached in memory for low‐latency playback. 




#### Soundplay Details: 
A thread is started for each note during the initialization process, and then the thread will listen for the signal to play the sound.
<br>
For the electronic timbres, the frequency of each note was stored in advance, and the tone generator could make up certain kinds of waves(sine, square, etc.) with the corresponding frequency. 

| Waveform | Formula                                        |
| -------- | ---------------------------------------------- |
| Sine     | `Math.sin(phase)`                              |
| Square   | `Math.signum(Math.sin(phase))`                 |
| Triangle | `(2.0 / Math.PI) * Math.asin(Math.sin(phase))` |
| Sawtooth | `(2.0 * (phase / (2.0 * Math.PI))) - 1.0`      |

For the real piano sound, an open-source sound pack [TEDAgame's Piano Pack](https://freesound.org/people/TEDAgame/packs/25405/) was used as the sound sample.
<br>
A metronome is also built into this App. A thread is responsible for playing beep sounds from the metronome, hence avoiding conflict with the piano keyboard.
<br>
The chord is implemented by a map to indicate the pitch difference between different chords.


### 4. Conclusion and Future Work

This project integrates concurrency, file I/O, socket networking, and GUI graphics in a cohesive Java application. Future enhancements could include:

* Adding JDBC support to store session metadata in a database, like login information, timbre preset and so on.
* Enabling multi-room support and user authentication.
* Enabling importing new timbre and chord map.

---

*Report prepared by Zhaodong Liu*
