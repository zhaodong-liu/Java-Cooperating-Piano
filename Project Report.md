## Final Project Report: Collaborative Java Swing Piano Application

### 1. Abstract

This project delivers a networked piano application built in Java that enables two users to play and chat in real time, record the piano session to a file, and replay it with accurate timing. The implementation addresses four core topics from the course requirements—thread concurrency with synchronization, file I/O, networking via sockets, and GUI graphics—in distinct modules to ensure clarity, modularity, and maintainability.

### 2. Introduction

With the option to substitute the final exam, this project demonstrates mastery of advanced Java features. The application architecture comprises the following components:

* **Graphics**: Java Swing-based piano keyboard GUI with Metronome using graphical methods.
* **Network Module**: TCP socket communication for note events and text messages.
* **Concurrency Module**: Multi-threaded management of audio playback, recording, network I/O, metronome, and GUI events.
* **Persistence Module**: File-based storage and retrieval of recorded sessions, real piano sample sound loading.

### 3. Implementation Details

#### 3.1 Thread Concurrency & Synchronization

* **Where:** PlaybackManager, NetworkHandler, Metronome and GUI event handlers in PianoApp.
* **Description:**

  * The `PendulumPanel` nested class in `Metronome.java` overrides `paintComponent` to draw the metronome housing and calibration scale.
  * It renders a black polygon housing using a `Polygon` with points calculated from the panel dimensions (`w` and `h`).
  * A vertical scale with tick marks (every 10 BPM) is drawn along a center line between `scaleTop` and `scaleBot`, using `drawLine` in white.
  * The pivot point is drawn as a light-gray circle at the bottom center (`fillOval`), serving as the anchor for the pendulum.
  * The pendulum rod is drawn by applying an `AffineTransform` rotation (`angle` computed from beat timing) and drawing a thick gray line upward from the pivot.
  * A red weight rectangle (`fillRect`) moves along the rod based on `weightFrac`, which adjusts according to the current BPM; its outline is drawn in light gray for contrast.
  * All rendering occurs in `paintComponent` and is triggered by `repaint()` calls in the `update` and `reset` methods of `PendulumPanel`, ensuring smooth animations.### 6. Testing and Validation
* Unit tests confirm that `RecordingManager` correctly writes and reads event sequences.
* Simulated multi-user sessions on localhost verify synchronization and networking reliability.
* GUI responsiveness is tested by rapid user input and simultaneous network events, ensuring no deadlocks.

### 7. Conclusion and Future Work

This project satisfies the course requirement by integrating concurrency, file I/O, socket networking, and GUI graphics in a cohesive Java application. Future enhancements could include:

* Adding **JDBC** support to store session metadata in a database.
* Enabling multi-room support and user authentication.
* Enhancing audio timbre options via plugin architecture.

---

*Report prepared by \[Zhaodong Liu], submitted May 10, 2025.*
