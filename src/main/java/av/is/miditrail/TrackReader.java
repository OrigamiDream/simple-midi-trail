package av.is.miditrail;

import javax.sound.midi.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class TrackReader {

    private final File file;
    private final Sequence sequence;

    private final Map<Integer, List<Note>> tracks = new HashMap<>();
    private final List<Line> lines = new ArrayList<>();

    public TrackReader(File file) throws InvalidMidiDataException, IOException {
        this.file = file;
        this.sequence = MidiSystem.getSequence(file);
    }

    public void loadTrack() {
        AtomicInteger atomicTrackId = new AtomicInteger();
        AtomicLong atomicTick = new AtomicLong();

        System.out.println("Loading MIDI file: " + file.getAbsolutePath());

        Stream.of(sequence.getTracks()).forEach(track -> {
            int trackId = atomicTrackId.getAndIncrement();

            tracks.put(trackId, new ArrayList<>());

            List<Note> notes = tracks.get(trackId);
            Map<Integer, Note> pendingNotes = new HashMap<>();
            for(int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);

                long tick = event.getTick();

                MidiMessage message = event.getMessage();
                if(message instanceof ShortMessage) {
                    ShortMessage shortMessage = (ShortMessage) message;

                    int key = shortMessage.getData1();
                    int velocity = shortMessage.getData2();

                    Runnable noteOn = () -> {
                        Note note = new Note(tick, key);
                        pendingNotes.put(key, note);
                    };

                    Runnable noteOff = () -> {
                        Note pending = pendingNotes.get(key);
                        if (pending != null) {
                            pending.setEndTick(tick);
                            notes.add(pending);
                            pendingNotes.remove(key);
                        }
                    };

                    switch (shortMessage.getCommand()) {
                        case ShortMessage.NOTE_ON:
                            if(velocity == 0) {
                                noteOff.run();
                            } else {
                                noteOn.run();
                            }
                            break;

                        case ShortMessage.NOTE_OFF:
                            noteOff.run();
                            break;

                        case 0x58: // All Notes Off
                            for(Note pendingNote : pendingNotes.values()) {
                                pendingNote.setEndTick(tick);
                                notes.add(pendingNote);
                            }
                            pendingNotes.clear();
                            break;
                    }
                }

                long prevTick = atomicTick.get();
                if(tick > prevTick) {
                    atomicTick.set(tick);
                }
            }

            System.out.println(notes.size() + " Notes of Track #" + trackId);
        });

        for(long i = 0; i < atomicTick.get(); i += sequence.getResolution() * 4) {
            lines.add(new Line(i));
        }
    }

    public List<Line> getLines() {
        return lines;
    }

    public Map<Integer, List<Note>> getTracks() {
        return tracks;
    }

    public Sequence getSequence() {
        return sequence;
    }
}
