# Simple MIDI Trail
For Black-MIDI Lovers

Simple MIDI Trail is the best MIDITrail and MAMPlayer alternative.<br/>
Cross-platform, and gorgeous minimalism design.

## Executable File / Downloads
You can find and download binary file of the Simple MIDI Trail.

Download: https://github.com/OrigamiDream/simple-midi-trail/releases

Download latest release `MIDITrail.jar`

You need `at least Java 8` to run and you can run the file by following simple command:
```bash
java -jar MIDITrail.jar
```

## How-to
First you run Simple MIDI Trail, you can choose a MIDI file you want to see trails by a shortcut (**Command** or **Ctrl** + **O**)<br/>
Tap **space** key, the magnificent music is now played and you enjoy the beautiful waterfalls.

## Shortcuts
- **Command** or **Ctrl** + **O** - Open a MIDI file (only *.mid and *.midi available)
- **Command** or **Ctrl** + **D** - Open a soundfont file (only *.sf2 available)
- **Command** or **Ctrl** + **B** - Toggle whether the MIDI summary show or not
- **Command** or **Ctrl** + **P** - You can choose several MIDI files to add to playlist
- **Space** - Play or stop playing MIDI file

## Screenshots
![Preview1](https://github.com/OrigamiDream/simple-midi-trail/tree/master/images/prev1.png)

![Preview2](https://github.com/OrigamiDream/simple-midi-trail/tree/master/images/prev2.png)


## Build
For macOS,
```bash
git clone https://github.com/OrigamiDream/juikit.git
git clone https://github.com/OrigamiDream/simple-midi-trail.git
cd juikit
./mvnw install
cd ../simple-midi-trail
./mvnw package
```

For Windows,
```bash
git clone https://github.com/OrigamiDream/juikit.git
git clone https://github.com/OrigamiDream/simple-midi-trail.git
cd juikit
mvnw.cmd install
cd ../simple-midi-trail
mvnw.cmd package
```

## License
This project follows AGPL-3.0.

Copyright (C) 2019 OrigamiDream