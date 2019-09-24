package studio.avis.miditrail;

import javax.sound.midi.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class TrackReader {

    private final File file;
    private final Sequence sequence;

    private final Map<Integer, List<Note>> tracks = new HashMap<>();
    private final List<Line> lines = new ArrayList<>();
    private long tickFinish = 0L;

    public TrackReader(File file) throws InvalidMidiDataException, IOException {
        this.file = file;
        this.sequence = MidiSystem.getSequence(file);
    }

    class TimeSignature {

        long tick = 0;

        int numerator = 4;
        int denominator = 4;

    }

    public void loadTrack() {
        AtomicInteger atomicTrackId = new AtomicInteger();
        AtomicLong atomicTick = new AtomicLong();

        System.out.println("Loading MIDI file: " + file.getAbsolutePath());

        List<TimeSignature> signatures = new ArrayList<>();

        AtomicBoolean signatureDefined = new AtomicBoolean(false);

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
                } else if(message instanceof MetaMessage) {
                    MetaMessage metaMessage = (MetaMessage) message;
                    if(metaMessage.getType() == 0x58 && !signatureDefined.get()) { // TIME SIGNATURE
                        byte[] data = ((MetaMessage) message).getData();

                        TimeSignature timeSignature = new TimeSignature();
                        timeSignature.tick = tick;
                        timeSignature.numerator = data[0] & 0xFF;
                        timeSignature.denominator = 1 << (data[1] & 0xFF);

                        signatures.add(timeSignature);
                    } else if(((MetaMessage) message).getType() == 0x2F) {
                        tickFinish = Math.max(tickFinish, tick);
                    }
                }

                long prevTick = atomicTick.get();
                if(tick > prevTick) {
                    atomicTick.set(tick);
                }
            }

            System.out.println(notes.size() + " Notes of Track #" + trackId);
            if(!signatureDefined.get()) {
                signatureDefined.set(true);
            }
        });

        if(signatures.isEmpty()) {
            signatures.add(new TimeSignature());
        }

        for(int i = 0; i < signatures.size(); i++) {
            TimeSignature signature = signatures.get(i);
            TimeSignature nextSignature = null;
            if(i + 1 != signatures.size()) {
                nextSignature = signatures.get(i + 1);
            }

            int step = sequence.getResolution() * 4 / signature.denominator * signature.numerator;

            if(nextSignature != null) {
                for(long j = signature.tick; j < nextSignature.tick; j += step) {
                    lines.add(new Line(j));
                }
            } else {
                for(long j = signature.tick; j < atomicTick.get(); j += step) {
                    lines.add(new Line(j));
                }
            }
        }
    }

    public long getTickFinish() {
        return tickFinish;
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
