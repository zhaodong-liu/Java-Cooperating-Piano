## This is the final project for CS-UY 3913 Java and Web Design
## Features:
The emulated piano keyboard with notes from C4 to C7, with one real sampled piano sound pack and 4 electronic sounds.
<br>
Support connection between multiple users, users can play piano together, and even have a simple chat system for users to share music thoughts or whatever.
<br>
Record and save the music in a simple format, and also support play from a local file.

## Implementation: 
### Soundplay: 
A thread is started for each note at the initialization process, and then the thread will listen for the signal to play the sound.
<br>
For the electronic timbres, the frequency of each note was stored in advance, and the tone generator could make up certain kinds of waves(sine, square, etc.) with the corresponding frequency. 
<br>
For the real piano sound, an open-source sound pack was used as the sound sample. The sound files will be loaded in advance and be played when the key is clicked. 
