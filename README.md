## Multiuser Piano
This project is a virtual piano application that supports **real-time multi-user collaboration**, **music recording/playback**, and **customizable sound generation**.  
Developed as the final project for **CS-UY 3913 Java and Web Design**.
## Features:
The emulated piano keyboard with notes from C4 to C7, with one real sampled piano sound pack and 4 electronic sounds.
<br>
Support connection between multiple users, users can play piano together, and even have a simple chat system for users to share music thoughts or whatever.
<br>
Record and save the music in a simple format, and also support play from a local file.

## Implementation: 
### Soundplay: 
A thread is started for each note during the initialization process, and then the thread will listen for the signal to play the sound.
<br>
For the electronic timbres, the frequency of each note was stored in advance, and the tone generator could make up certain kinds of waves(sine, square, etc.) with the corresponding frequency. 
<br>
  | Waveform   | Formula                          |
  |------------|-----------------------------------|
  | Sine       | `Math.sin(phase)`                 |
  | Square     | `Math.signum(Math.sin(phase))`     |
  | Triangle   | `(2.0 / Math.PI) * Math.asin(Math.sin(phase))` |
  | Sawtooth   | `(2.0 * (phase / (2.0 * Math.PI))) - 1.0` |
<br>
For the real piano sound, an open-source sound pack [TEDAgame's Piano Pack](https://freesound.org/people/TEDAgame/packs/25405/) was used as the sound sample. The sound files will be loaded in advance and be played when the key is clicked. 


### Server:
The server handles the connection between users and listens for two kinds of messages: "MUSIC" and "CHAT", once received message, it will broadcast the message to all the users.

### (Play from/Save to) File:
The notes are saved in format: note,startTime,endTime, timbre, which is easy to code and modify outside. It is possible to compose some music in this format and play it in the App.
