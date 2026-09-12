package studio.avis.miditrail;

import javax.sound.midi.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

/** Reads half-open MIDI note intervals without altering the playback sequence. */
public class TrackReader {
    private final File file;
    private final Sequence sequence;
    private final Map<Integer, List<Note>> tracksForSheet = new TreeMap<>();
    private final Map<Integer, List<Note>> tracksForPianoRoll = new TreeMap<>();
    private final List<Line> lines = new ArrayList<>();
    private final Set<Temp> temps = new LinkedHashSet<>();
    private final List<TimeSignature> timeSignatures = new ArrayList<>();
    private final List<KeySignature> keySignatures = new ArrayList<>();
    private int lowestKey = 21;
    private int highestKey = 108;

    public TrackReader(File file) throws InvalidMidiDataException, IOException {
        this.file = file;
        this.sequence = MidiSystem.getSequence(file);
    }

    public boolean findNoteEquivalent(int track, long timestamp, int key) {
        for (Note note : tracksForSheet.getOrDefault(track, Collections.emptyList())) {
            if (note.getFromTick() == timestamp && note.getKey() == key) return true;
        }
        return false;
    }

    public void loadTrack() {
        tracksForSheet.clear();
        tracksForPianoRoll.clear();
        lines.clear();
        temps.clear();
        timeSignatures.clear();
        keySignatures.clear();
        lowestKey = 21;
        highestKey = 108;
        TreeMap<Long, KeySignature> keys = new TreeMap<>();
        TreeMap<Long, TimeSignature> meters = new TreeMap<>();
        TreeMap<Long, Temp> tempos = new TreeMap<>();
        keys.put(0L, new KeySignature(0, KeySignature.Gender.MAJOR, 7));
        meters.put(0L, new TimeSignature(0, 4, 4));
        tempos.put(0L, new Temp(0, 120));
        long musicalEnd = 0;
        int trackId = 0;
        for (Track track : sequence.getTracks()) {
            List<Note> notes = new ArrayList<>();
            // A track can contain several channels and repeated overlapping pitches.
            Map<Integer, Deque<Note>> pending = new HashMap<>();
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                long tick = event.getTick();
                MidiMessage message = event.getMessage();
                if (message instanceof ShortMessage) {
                    ShortMessage m = (ShortMessage) message;
                    int id = m.getChannel() * 128 + m.getData1();
                    if (m.getCommand() == ShortMessage.NOTE_ON && m.getData2() > 0) {
                        pending.computeIfAbsent(id, k -> new ArrayDeque<>())
                                .addLast(new Note(tick, m.getData1(), m.getData2()));
                        lowestKey = Math.min(lowestKey, m.getData1());
                        highestKey = Math.max(highestKey, m.getData1());
                    } else if (m.getCommand() == ShortMessage.NOTE_OFF ||
                            (m.getCommand() == ShortMessage.NOTE_ON && m.getData2() == 0)) {
                        Deque<Note> queue = pending.get(id);
                        if (queue != null && !queue.isEmpty()) finish(queue.removeFirst(), tick, notes);
                    } else if (m.getCommand() == ShortMessage.CONTROL_CHANGE &&
                            (m.getData1() == 120 || m.getData1() == 123)) {
                        for (Map.Entry<Integer, Deque<Note>> entry : pending.entrySet()) {
                            if (entry.getKey() / 128 == m.getChannel()) {
                                for (Note note : entry.getValue()) finish(note, tick, notes);
                                entry.getValue().clear();
                            }
                        }
                    }
                } else if (message instanceof MetaMessage) {
                    MetaMessage m = (MetaMessage) message;
                    byte[] data = m.getData();
                    if (m.getType() == 0x58 && data.length >= 2 && (data[0] & 255) > 0 && (data[1] & 255) <= 7) {
                        meters.put(tick, new TimeSignature(tick, data[0] & 255, 1 << (data[1] & 255)));
                    } else if (m.getType() == 0x59 && data.length >= 2 && data[0] >= -7 && data[0] <= 7) {
                        keys.put(tick, new KeySignature(tick, data[1] == 1 ? KeySignature.Gender.MINOR : KeySignature.Gender.MAJOR, data[0] + 7));
                    } else if (m.getType() == 0x51 && data.length == 3) {
                        int micros = ((data[0] & 255) << 16) | ((data[1] & 255) << 8) | (data[2] & 255);
                        if (micros > 0) tempos.put(tick, new Temp(tick, Math.round(60000000f / micros)));
                    }
                }
            }
            for (Deque<Note> queue : pending.values()) {
                for (Note note : queue) finish(note, track.ticks(), notes);
            }
            notes.sort(Comparator.comparingLong(Note::getFromTick).thenComparingInt(Note::getKey).thenComparingLong(Note::getEndTick));
            for (Note note : notes) musicalEnd = Math.max(musicalEnd, note.getEndTick());
            tracksForPianoRoll.put(trackId, notes);
            List<Note> sheet = new ArrayList<>();
            for (Note note : notes) sheet.add(note.copy());
            tracksForSheet.put(trackId++, sheet);
        }
        keySignatures.addAll(keys.values());
        timeSignatures.addAll(meters.values());
        temps.addAll(tempos.values());
        // Ignore trailing empty-track EOT padding, common in Black MIDI files.
        long end = Math.max(1, musicalEnd);
        long tick = 0;
        while (tick < end || lines.isEmpty()) {
            lines.add(new Line(tick));
            TimeSignature meter = meters.floorEntry(tick).getValue();
            long step = Math.max(1, Math.round(sequence.getResolution() * 4.0 * meter.getNumerator() / meter.getDenominator()));
            Long change = meters.higherKey(tick);
            tick = change == null ? tick + step : Math.min(tick + step, change);
        }
        Line finalLine = new Line(tick);
        finalLine.setFinale(true);
        lines.add(finalLine);
        System.out.println("Loaded " + file.getName() + ": " + (lines.size() - 1) + " measures, " +
                tracksForPianoRoll.values().stream().mapToInt(List::size).sum() + " notes");
    }

    private static void finish(Note note, long tick, List<Note> notes) {
        note.setEndTick(Math.max(note.getFromTick() + 1, tick));
        notes.add(note);
    }

    public Map<Integer, List<Note>> findNotesBetween(long fromTick, long toTick) {
        Map<Integer, List<Note>> result = new TreeMap<>();
        for (Map.Entry<Integer, List<Note>> entry : tracksForSheet.entrySet()) {
            List<Note> notes = new ArrayList<>();
            for (Note note : entry.getValue()) {
                if (note.getFromTick() >= toTick) break;
                if (note.getEndTick() > fromTick) notes.add(note);
            }
            result.put(entry.getKey(), notes);
        }
        return result;
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
