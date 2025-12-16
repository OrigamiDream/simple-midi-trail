package studio.avis.miditrail;

import studio.avis.miditrail.screens.views.TrackSheetView;

import javax.sound.midi.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TrackReader {

    private static final String[] KEY_SIGNATURE_NAMES = {"Cb", "Gb", "Db", "Ab", "Eb", "Bb", "F", "C", "G", "D", "A", "E", "B", "F#", "C#"};

    private final File file;
    private final Sequence sequence;

    private final Map<Integer, List<Note>> tracksForSheet = new HashMap<>();
    private final Map<Integer, List<Note>> tracksForPianoRoll = new HashMap<>();
    private final List<Line> lines = new ArrayList<>();
    private final Set<Temp> temps = new HashSet<>();
    private final List<TimeSignature> timeSignatures = new ArrayList<>();
    private final List<KeySignature> keySignatures = new ArrayList<>();

    private int lowestKey = 21;
    private int highestKey = 108;

    public TrackReader(File file) throws InvalidMidiDataException, IOException {
        this.file = file;
        this.sequence = MidiSystem.getSequence(file);
    }

    public boolean findNoteEquivalent(int channel, long timestamp, int key) {
        List<Note> notes = tracksForSheet.get(channel);
        for(Note note : notes) {
            if(note.getFromTick() == timestamp && note.getKey() == key) {
                return true;
            }
        }
        return false;
    }

    public void loadTrack() {
        AtomicInteger atomicTrackId = new AtomicInteger();
        AtomicLong atomicTick = new AtomicLong();

        System.out.println("Loading MIDI file: " + file.getAbsolutePath());

        List<KeySignature> keySignatures = new ArrayList<>();
        List<TimeSignature> timeSignatures = new ArrayList<>();
        List<Temp> temps = new ArrayList<>();

        AtomicBoolean keySignatureDefined = new AtomicBoolean(false);
        AtomicBoolean timeSignatureDefined = new AtomicBoolean(false);
        AtomicBoolean tempDefined = new AtomicBoolean(false);

        Stream.of(sequence.getTracks()).forEach(track -> {
            int trackId = atomicTrackId.getAndIncrement();

            tracksForPianoRoll.put(trackId, new ArrayList<>());
            tracksForSheet.put(trackId, new ArrayList<>());

            List<Note> notesForPianoRoll = tracksForPianoRoll.get(trackId);
            List<Note> notesForSheet = tracksForSheet.get(trackId);
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
                        Note note = new Note(tick, key, velocity);
                        pendingNotes.put(key, note);

                        if(key < lowestKey) {
                            lowestKey = key;
                        }

                        if(key > highestKey) {
                            highestKey = key;
                        }
                    };

                    Runnable noteOff = () -> {
                        Note pending = pendingNotes.get(key);
                        if (pending != null) {
                            pending.setEndTick(tick);

//                            long duration = pending.getEndTick() - pending.getFromTick();
//                            int v = pending.getVelocity();
//                            if(duration > 2 && v > 50) {
                                notesForPianoRoll.add(pending.copy());
                                notesForSheet.add(pending.copy());
//                            }
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
                                notesForPianoRoll.add(pendingNote.copy());
                                notesForSheet.add(pendingNote.copy());
                            }
                            pendingNotes.clear();
                            break;
                    }
                } else if(message instanceof MetaMessage) {
                    MetaMessage metaMessage = (MetaMessage) message;
                    byte[] data = metaMessage.getData();
                    if(metaMessage.getType() == 0x58 && !timeSignatureDefined.get()) { // TIME SIGNATURE
                        TimeSignature timeSignature = new TimeSignature(tick, data[0] & 0xFF, 1 << (data[1] & 0xFF));

                        timeSignatures.add(timeSignature);

                        System.out.println("Found time signature [" + timeSignature.getNumerator() + " / " + timeSignature.getDenominator() + "] at " + tick + " in track #" + trackId + ".");
                    } else if(metaMessage.getType() == 0x51 && !tempDefined.get()) { // TEMP
                        float bpm = (float) (((data[0] & 0xFF) << 16) | ((data[1] & 0xFF) << 8) | (data[2] & 0xFF));
                        if(bpm <= 0) {
                            bpm = 0.1f;
                        }
                        bpm = 60000000.0f / bpm;

                        Temp temp = new Temp(tick, (int) bpm);
                        temps.add(temp);
                    } else if(metaMessage.getType() == 0x59 && !keySignatureDefined.get()) { // KEY SIGNATURE
                        KeySignature.Gender gender = data[1] == 1 ? KeySignature.Gender.MINOR : KeySignature.Gender.MAJOR;
                        int signature = data[0] + 7;

                        KeySignature keySignature = new KeySignature(tick, gender, signature);
                        if(!keySignatures.contains(keySignature)) {
                            keySignatures.add(keySignature);
                        }

                        System.out.println("Found key signature [" + KEY_SIGNATURE_NAMES[signature] + ", " + gender.toString() + " - flats: " + keySignature.getFlats() + ", sharps: " + keySignature.getSharps() + "] at " + tick + " in track #" + trackId + ".");
                    }
                }

                long prevTick = atomicTick.get();
                if(tick > prevTick) {
                    atomicTick.set(tick);
                }
            }

            System.out.println(notesForPianoRoll.size() + " Notes of Track #" + trackId);
            if(!keySignatureDefined.get()) {
                keySignatureDefined.set(true);
            }

            if(!timeSignatureDefined.get()) {
                timeSignatureDefined.set(true);
            }

            if(!tempDefined.get()) {
                tempDefined.set(true);
            }
        });

        if(keySignatures.isEmpty()) {
            keySignatures.add(new KeySignature(0, KeySignature.Gender.MAJOR, 7)); // C-major
        }

        if(timeSignatures.isEmpty()) {
            timeSignatures.add(new TimeSignature(0, 4, 4)); // 4/4 at tick 0
        }

        for(int i = 0; i < timeSignatures.size(); i++) {
            TimeSignature signature = timeSignatures.get(i);
            TimeSignature nextSignature = null;
            if(i + 1 != timeSignatures.size()) {
                nextSignature = timeSignatures.get(i + 1);
            }

            int step = sequence.getResolution() * 4 / signature.getDenominator() * signature.getNumerator();

            if(nextSignature != null) {
                for(long j = signature.getTick(); j < nextSignature.getTick(); j += step) {
                    lines.add(new Line(j));
                }
            } else {
                for(long j = signature.getTick(); j < atomicTick.get(); j += step) {
                    lines.add(new Line(j));
                }
            }
        }

        int finaleIndex = lines.size() + 1;

        int more = (TrackSheetView.MEASURES_PER_PAGE - 1) * 5; // 5 more pages
        TimeSignature lastSignature = timeSignatures.get(timeSignatures.size() - 1);
        Line lastLine = lines.get(lines.size() - 1);
        int step = sequence.getResolution() * 4 / lastSignature.getDenominator() * lastSignature.getNumerator();

        for(int i = 0; i < more; i++) {
            lines.add(new Line(lastLine.getTick() + ((i + 1) * step)));
        }
        lines.get(finaleIndex).setFinale(true);

        System.out.println("Total bar lines: " + lines.size());

        System.out.println("Processing notes...");

        tracksForSheet.values().forEach(notes -> {
            List<Note> additional = new ArrayList<>();
            notes.forEach(note -> {
                long fromTick = note.getFromTick();
                long endTick = note.getEndTick();

                List<Note> newNotes = new ArrayList<>();
                for (Line line : lines) {
                    boolean completed = endTick < line.getTick();

                    if(fromTick < line.getTick()) {
                        long length;
                        if(completed) {
                            length = endTick - fromTick;
                        } else {
                            length = line.getTick() - fromTick - 1;
                        }
                        if(length > 0) {
                            Note newNote = new Note(fromTick, note.getKey(), note.getVelocity());
                            newNote.setEndTick(fromTick + length);
                            newNotes.add(newNote);
                            fromTick = line.getTick();
                        }
                    }
                    if(completed) {
                        if(!newNotes.isEmpty()) {
                            note.setEndTick(newNotes.get(0).getEndTick());
                            for(int i = 1; i < newNotes.size(); i++) {
                                additional.add(newNotes.get(i));
                            }
                        }
                        break;
                    }
                }
            });
            notes.addAll(additional);
        });

//        tracks.values().forEach(notes -> notes.forEach(note -> {
//            long fromTick = note.getFromTick();
//            long endTick = note.getEndTick();
//            Line prev = null;
//            System.out.println("START----");
//            for(Line line : lines) {
//                long lineTick = line.getTick();
//
//                long measureLength = prev == null ? lineTick : lineTick - prev.getTick();
//                int timeSignature = findTimeSignatureBetween(prev == null ? 0 : prev.getTick(), lineTick);
//                if(timeSignature != -1) {
//                    System.out.println("Failed to get time signature between " + (prev == null ? 0 : prev.getTick()) + " ~ " + lineTick + ".");
//                }
//                // TODO set notes length using measure length and current time signature.
//
//                if(lineTick >= fromTick && (prev == null || prev.getTick() <= fromTick)) {
//                    long noteLength;
//                    if(lineTick < endTick) {
//                        noteLength = lineTick - fromTick;
//                    } else {
//                        noteLength = endTick - fromTick;
//                    }
//
//                    System.out.println(fromTick + " -> " + (fromTick + noteLength) + " (+" + noteLength + ")");
//                    note.getTicks().add(new AbstractMap.SimpleEntry<>(fromTick, fromTick + noteLength));
//                    fromTick += noteLength;
//                }
//                prev = line;
//            }
//            System.out.println("END----");
//        }));
        System.out.println("Processing notes are completed.");

        this.temps.addAll(temps);
        this.keySignatures.addAll(keySignatures);
        this.timeSignatures.addAll(timeSignatures);
    }

    public Map<Integer, List<Note>> findNotesBetween(long fromTick, long toTick) {
        Map<Integer, List<Note>> tracks = new HashMap<>();
        for(Map.Entry<Integer, List<Note>> entry : getTracksForSheet().entrySet()) {
            tracks.put(entry.getKey(), entry.getValue()
                    .stream()
                    .filter(note -> fromTick <= note.getFromTick() && note.getEndTick() < toTick).collect(Collectors.toList()));
        }
        return tracks;
    }

    public int findKeySignatureBetween(long fromTick, long toTick) {
        List<KeySignature> signatures = getKeySignatures();
        for(int i = 0; i < signatures.size(); i++) {
            KeySignature signature = signatures.get(i);
            if(fromTick <= signature.getTick() && signature.getTick() < toTick) {
                return i;
            }
        }
        return -1;
    }

    public int findTimeSignatureBetween(long fromTick, long toTick) {
        List<TimeSignature> signatures = getTimeSignatures();
        for(int i = 0; i < signatures.size(); i++) {
            TimeSignature signature = signatures.get(i);
            if(fromTick <= signature.getTick() && signature.getTick() < toTick) {
                return i;
            }
        }
        return -1;
    }

    public KeySignature findKeySignatureBefore(long tick) {
        List<KeySignature> signatures = getKeySignatures();
        KeySignature last = signatures.get(0);
        for(int i = 1; i < signatures.size(); i++) {
            KeySignature cur = signatures.get(i);
            if(cur.getTick() > tick) {
                return last;
            } else {
                last = cur;
            }
        }
        return last;
    }

    public TimeSignature findTimeSignatureBefore(long tick) {
        List<TimeSignature> signatures = getTimeSignatures();
        TimeSignature last = signatures.get(0);
        for(int i = 1; i < signatures.size(); i++) {
            TimeSignature cur = signatures.get(i);
            if(cur.getTick() > tick) {
                return last;
            } else {
                last = cur;
            }
        }
        return last;
    }

    public List<KeySignature> getKeySignatures() {
        return keySignatures;
    }

    public List<TimeSignature> getTimeSignatures() {
        return timeSignatures;
    }

    public Set<Temp> getTemps() {
        return temps;
    }

    public List<Line> getLines() {
        return lines;
    }

    public Map<Integer, List<Note>> getTracksForSheet() {
        return tracksForSheet;
    }

    public Map<Integer, List<Note>> getTracksForPianoRoll() {
        return tracksForPianoRoll;
    }

    public Sequence getSequence() {
        return sequence;
    }

    public int getLowestKey() {
        return lowestKey;
    }

    public int getHighestKey() {
        return highestKey;
    }
}
