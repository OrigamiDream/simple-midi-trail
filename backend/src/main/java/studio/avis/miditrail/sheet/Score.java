package studio.avis.miditrail.sheet;

import studio.avis.miditrail.*;
import javax.sound.midi.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Deterministic, display-only transcription. All times are half-open MIDI ticks. */
public final class Score {
    public static final int NONE = 2;
    public final int resolution;
    public final List<Measure> measures = new ArrayList<>();
    public final List<Mark> marks = new ArrayList<>();
    public final List<Pedal> pedals = new ArrayList<>();
    public final boolean metrical;
    /** Skip wholly silent lead-in bars in the written score, without changing playback ticks. */
    public int firstSoundingMeasure;
    private final List<Rhythm> rhythms = new ArrayList<>();
    private static final int[] NATURAL = {0, 2, 4, 5, 7, 9, 11};
    private static final int[] SHARPS = {3, 0, 4, 1, 5, 2, 6};
    private static final int[] FLATS = {6, 2, 5, 1, 4, 0, 3};

    public static final class Rhythm {
        public final int denominator, dots, tuplet;
        public final double ticks;
        Rhythm(int denominator, int dots, int tuplet, double ticks) {
            this.denominator = denominator; this.dots = dots; this.tuplet = tuplet; this.ticks = ticks;
        }
        public int beams() { return Math.max(0, Integer.numberOfTrailingZeros(denominator) - 2); }
    }
    public static final class Pitch {
        public final int step, alteration;
        Pitch(int step, int alteration) { this.step = step; this.alteration = alteration; }
    }
    public static final class Head {
        public final Note source;
        public final Pitch pitch;
        public final boolean tieIn, tieOut;
        public final long notatedEnd;
        public int accidental = NONE;
        Head(Note source, Pitch pitch, boolean tieIn, boolean tieOut, long notatedEnd) {
            this.source = source; this.pitch = pitch; this.tieIn = tieIn; this.tieOut = tieOut;
            this.notatedEnd = notatedEnd;
        }
    }
    public static final class Chord {
        public final long start, end;
        public final int staff;
        public final Rhythm rhythm;
        public final List<Head> heads = new ArrayList<>();
        public int voice;
        public boolean rest, wholeRest;
        Chord(long start, long end, int staff, Rhythm rhythm) {
            this.start = start; this.end = end; this.staff = staff; this.rhythm = rhythm;
        }
    }
    public static final class Measure {
        public final int index;
        public final long start, end;
        public final KeySignature key;
        public final TimeSignature meter;
        public final List<Chord> chords = new ArrayList<>();
        Measure(int index, long start, long end, KeySignature key, TimeSignature meter) {
            this.index = index; this.start = start; this.end = end; this.key = key; this.meter = meter;
        }
        public long beatTicks(int resolution) {
            int compound = meter.getNumerator() > 3 && meter.getNumerator() % 3 == 0 && meter.getDenominator() >= 8 ? 3 : 1;
            return Math.max(1, Math.round(resolution * 4.0 * compound / meter.getDenominator()));
        }
    }
    public static final class Mark {
        public final long tick;
        public final String text;
        public final boolean tempo;
        Mark(long tick, String text, boolean tempo) { this.tick = tick; this.text = text; this.tempo = tempo; }
    }
    public static final class Pedal {
        public final long start, end;
        Pedal(long start, long end) { this.start = start; this.end = end; }
    }

    public Score(TrackReader reader) {
        resolution = reader.getSequence().getResolution();
        metrical = reader.getSequence().getDivisionType() == Sequence.PPQ;
        if (!metrical) return; // SMPTE ticks are wall time, not quarter notes.
        for (int d = 1; d <= 256; d *= 2) {
            for (int dots = 0; dots <= 2; dots++) {
                for (int tuplet : new int[]{0, 3, 5, 7}) {
                    int normal = tuplet == 3 ? 2 : 4;
                    double length = resolution * 4.0 / d * (2 - Math.pow(0.5, dots));
                    if (tuplet != 0) length *= normal / (double) tuplet;
                    rhythms.add(new Rhythm(d, dots, tuplet, length));
                }
            }
        }
        List<Line> lines = reader.getLines();
        for (int i = 0; i + 1 < lines.size(); i++) {
            long start = lines.get(i).getTick();
            measures.add(new Measure(i, start, lines.get(i + 1).getTick(), reader.findKeySignatureBefore(start), reader.findTimeSignatureBefore(start)));
        }
        List<Map<String, Chord>> groups = new ArrayList<>();
        for (Measure ignored : measures) groups.add(new HashMap<>());
        Map<Note, Long> displayEnds = displayEnds(reader);
        for (List<Note> track : reader.getTracksForSheet().values()) {
            for (Note note : track) {
                long start = note.getFromTick();
                long displayEnd = displayEnds.getOrDefault(note, note.getEndTick());
                int bar = measureAt(start);
                // Keep spelling across ties, including a key change.
                Pitch pitch = spell(note.getKey(), reader.findKeySignatureBefore(start), note.getKey() >= 60 ? 0 : 1);
                while (start < displayEnd && bar < measures.size()) {
                    Measure measure = measures.get(bar);
                    long limit = Math.min(displayEnd, measure.end);
                    for (Chord part : split(start, limit, note.getKey() >= 60 ? 0 : 1, measure)) {
                        String id = part.staff + ":" + part.start + ":" + part.end;
                        Chord chord = groups.get(bar).computeIfAbsent(id, k -> part);
                        // Identical unisons from several MIDI tracks share a single written head.
                        boolean duplicate = false;
                        for (Head head : chord.heads) {
                            if (head.source.getKey() == note.getKey() && head.source.getFromTick() == note.getFromTick()
                                    && head.source.getEndTick() == note.getEndTick()) { duplicate = true; break; }
                        }
                        if (!duplicate) chord.heads.add(new Head(note, pitch, part.start > note.getFromTick(), part.end < displayEnd, displayEnd));
                    }
                    start = limit;
                    bar++;
                }
            }
        }
        for (Measure measure : measures) {
            measure.chords.addAll(groups.get(measure.index).values());
            measure.chords.sort(Comparator.comparingLong((Chord c) -> c.start).thenComparingInt(c -> c.staff).thenComparingLong(c -> -c.end));
            assignVoices(measure);
            addRests(measure);
            measure.chords.sort(Comparator.comparingLong((Chord c) -> c.start).thenComparingInt(c -> c.staff).thenComparingInt(c -> c.voice));
            accidentals(measure, reader);
        }
        for (Measure measure : measures) {
            boolean sounding = false;
            for (Chord chord : measure.chords) sounding |= !chord.rest;
            if (sounding) { firstSoundingMeasure = measure.index; break; }
        }
        for (Temp tempo : reader.getTemps()) marks.add(new Mark(tempo.getTick(), "= " + tempo.getBpm(), true));
        readMarks(reader);
        marks.sort(Comparator.comparingLong(m -> m.tick));
    }

    public int measureAt(long tick) {
        int lo = 0, hi = measures.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (measures.get(mid).start <= tick) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    private Map<Note, Long> displayEnds(TrackReader reader) {
        List<TreeMap<Long, List<Note>>> staves = Arrays.asList(new TreeMap<>(), new TreeMap<>());
        for (List<Note> track : reader.getTracksForSheet().values()) for (Note note : track)
            staves.get(note.getKey() >= 60 ? 0 : 1).computeIfAbsent(note.getFromTick(), k -> new ArrayList<>()).add(note);
        Map<Note, Long> ends = new IdentityHashMap<>();
        for (TreeMap<Long, List<Note>> staff : staves) for (Map.Entry<Long, List<Note>> entry : staff.entrySet()) {
            long start = entry.getKey(), longestEnd = start;
            Set<Integer> pitches = new HashSet<>();
            for (Note note : entry.getValue()) { longestEnd = Math.max(longestEnd, note.getEndTick()); pitches.add(note.getKey()); }
            Measure measure = measures.get(measureAt(start));
            long beat = measure.beatTicks(resolution), duration = longestEnd - start;
            Long next = staff.higherKey(start);
            long displayEnd = longestEnd;
            // Short MIDI gates describe articulation, not necessarily written rhythm.
            // Combine a short chord on a shared stem and follow nearby attacks; keep
            // sustained independent voices and every cross-bar duration intact.
            if (duration > beat || longestEnd > measure.end) continue;
            if (next != null && next - start <= beat && next - start >= duration / 2.0 && next - start <= duration * 2) {
                displayEnd = Math.min(next, measure.end);
            } else if (pitches.size() >= 3 && duration <= beat / 2 && (next == null || next - start >= beat)) {
                // An isolated short chord occupies a beat, matching the supplied opening reference.
                displayEnd = Math.min(measure.end, start + beat);
            }
            for (Note note : entry.getValue()) ends.put(note, displayEnd);
        }
        return ends;
    }

    private List<Chord> split(long start, long end, int staff, Measure measure) {
        return split(start, end, staff, measure, true);
    }

    private List<Chord> split(long start, long end, int staff, Measure measure, boolean allowTuplets) {
        List<Chord> result = new ArrayList<>();
        while (start < end) {
            long remaining = end - start;
            Rhythm best = null;
            double cost = Double.MAX_VALUE;
            double grid = resolution / 64.0;
            boolean binaryGrid = Math.abs(remaining - Math.round(remaining / grid) * grid) <= .51;
            // Prefer an exact simple value. Small performance articulation gaps are display-quantized.
            for (Rhythm rhythm : rhythms) {
                if (!allowTuplets && !restValueAllowed(rhythm, start, measure)) continue;
                double absoluteError = Math.abs(rhythm.ticks - remaining);
                if (rhythm.tuplet != 0 && (!allowTuplets || binaryGrid || absoluteError > 1)) continue;
                // An exact binary duration such as five eighths must be decomposed, not approximated.
                if (binaryGrid && absoluteError > .51) continue;
                double error = Math.abs(rhythm.ticks - remaining) / Math.max(1, remaining);
                double candidate = error + rhythm.dots * 0.05 + (rhythm.tuplet == 0 ? 0 : 0.06);
                if (error <= 0.13 && candidate < cost) { best = rhythm; cost = candidate; }
            }
            long length = remaining;
            if (best == null) {
                double largest = 0;
                for (Rhythm rhythm : rhythms) {
                    if (rhythm.tuplet == 0 && rhythm.ticks <= remaining && rhythm.ticks > largest &&
                            (allowTuplets || restValueAllowed(rhythm, start, measure))) {
                        best = rhythm; largest = rhythm.ticks;
                    }
                }
                if (best == null) {
                    best = rhythms.get((8 * 3) * 4); // 256th, undotted; sub-grid durations remain visible.
                } else length = Math.max(1, Math.round(best.ticks));
            }
            // Syncopated non-tuplet durations crossing a beat split at that beat.
            long beat = measure.beatTicks(resolution);
            long offset = start - measure.start;
            long boundary = measure.start + (offset / beat + 1) * beat;
            if (!allowTuplets && offset % beat != 0 && start + length > boundary) {
                result.addAll(split(start, boundary, staff, measure, allowTuplets));
                start = boundary;
                continue;
            }
            result.add(new Chord(start, start + length, staff, best));
            start += length;
        }
        return result;
    }

    private boolean restValueAllowed(Rhythm rhythm, long start, Measure measure) {
        if (rhythm.tuplet != 0) return false;
        long beat = measure.beatTicks(resolution);
        double base = resolution * 4.0 / rhythm.denominator;
        if (rhythm.dots > 0) {
            if (measure.meter.getNumerator() <= 3 || measure.meter.getNumerator() % 3 != 0 ||
                    measure.meter.getDenominator() < 8 || Math.abs(rhythm.ticks - beat) > .51) return false;
            base = beat;
        }
        double offset = start - measure.start;
        return Math.abs(offset - Math.round(offset / base) * base) <= .51;
    }

    private static void assignVoices(Measure measure) {
        List<List<Long>> ends = Arrays.asList(new ArrayList<>(), new ArrayList<>());
        for (Chord chord : measure.chords) {
            List<Long> staff = ends.get(chord.staff);
            int voice = 0;
            while (voice < staff.size() && staff.get(voice) > chord.start) voice++;
            if (voice == staff.size()) staff.add(chord.end); else staff.set(voice, chord.end);
            chord.voice = voice;
            chord.heads.sort(Comparator.comparingInt(h -> h.pitch.step));
        }
    }

    private void addRests(Measure measure) {
        // Staff-wide silence only: do not invent rests for arbitrary Black MIDI voice allocation.
        List<Chord> rests = new ArrayList<>();
        for (int staff = 0; staff < 2; staff++) {
            long cursor = measure.start;
            boolean empty = true;
            for (Chord chord : measure.chords) {
                if (chord.staff != staff) continue;
                empty = false;
                if (chord.start - cursor > Math.max(1, resolution / 64)) rests.addAll(rests(cursor, chord.start, staff, measure));
                cursor = Math.max(cursor, chord.end);
            }
            if (empty) {
                Chord rest = new Chord(measure.start, measure.end, staff, rhythms.get(0));
                rest.rest = true; rest.wholeRest = true; rests.add(rest);
            } else if (measure.end - cursor > Math.max(1, resolution / 64)) {
                rests.addAll(rests(cursor, measure.end, staff, measure));
            }
        }
        measure.chords.addAll(rests);
    }

    private List<Chord> rests(long start, long end, int staff, Measure measure) {
        List<Chord> result = split(start, end, staff, measure, false);
        for (Chord chord : result) chord.rest = true;
        return result;
    }

    public static int keyAlteration(int step, KeySignature key) {
        int letter = Math.floorMod(step, 7);
        for (int i = 0; i < key.getSharps(); i++) if (SHARPS[i] == letter) return 1;
        for (int i = 0; i < key.getFlats(); i++) if (FLATS[i] == letter) return -1;
        return 0;
    }

    public static Pitch spell(int midi, KeySignature key) {
        return spell(midi, key, key.getFlats() > 0 ? 1 : 0);
    }

    public static Pitch spell(int midi, KeySignature key, int staff) {
        Pitch best = null;
        double score = Double.MAX_VALUE;
        int octave = midi / 12 - 1;
        for (int o = octave - 1; o <= octave + 1; o++) {
            for (int letter = 0; letter < 7; letter++) {
                int alt = midi - ((o + 1) * 12 + NATURAL[letter]);
                if (Math.abs(alt) > 1) continue;
                int step = o * 7 + letter;
                double cost = Math.abs(alt - keyAlteration(step, key)) * 2 + Math.abs(alt) * 0.2;
                // SSW-style piano spelling: key-signature tones first, then chromatic
                // sharps in the upper staff and flats in the lower staff.
                if ((staff == 1 && alt > 0) || (staff == 0 && alt < 0)) cost += 0.1;
                if (cost < score) { score = cost; best = new Pitch(step, alt); }
            }
        }
        return best;
    }

    private void accidentals(Measure measure, TrackReader reader) {
        Map<Integer, Integer> state = new HashMap<>();
        KeySignature key = measure.key;
        int at = 0;
        while (at < measure.chords.size()) {
            long tick = measure.chords.get(at).start;
            KeySignature current = reader.findKeySignatureBefore(tick);
            if (current.getTick() != key.getTick()) { state.clear(); key = current; }
            Map<Integer, Set<Integer>> simultaneous = new HashMap<>();
            int end = at;
            while (end < measure.chords.size() && measure.chords.get(end).start == tick) {
                Chord chord = measure.chords.get(end++);
                for (Head head : chord.heads) {
                    if (head.tieIn) continue;
                    int id = chord.staff * 256 + head.pitch.step + 16;
                    simultaneous.computeIfAbsent(id, k -> new HashSet<>()).add(head.pitch.alteration);
                }
            }
            Set<Integer> drawn = new HashSet<>();
            for (int i = at; i < end; i++) {
                Chord chord = measure.chords.get(i);
                for (Head head : chord.heads) {
                    int id = chord.staff * 256 + head.pitch.step + 16;
                    int previous = state.getOrDefault(id, keyAlteration(head.pitch.step, key));
                    if (!head.tieIn && (previous != head.pitch.alteration || simultaneous.get(id).size() > 1)
                            && drawn.add(id * 4 + head.pitch.alteration + 1)) head.accidental = head.pitch.alteration;
                }
            }
            for (Map.Entry<Integer, Set<Integer>> entry : simultaneous.entrySet()) {
                // Ambiguous simultaneous alterations require a fresh accidental on the next attack.
                state.put(entry.getKey(), entry.getValue().size() > 1 ? NONE : entry.getValue().iterator().next());
            }
            at = end;
        }
    }

    private void readMarks(TrackReader reader) {
        class Event {
            long tick; int channel, value;
            Event(long tick, int channel, int value) { this.tick = tick; this.channel = channel; this.value = value; }
        }
        List<Event> events = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Track track : reader.getSequence().getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                if (event.getMessage() instanceof ShortMessage) {
                    ShortMessage m = (ShortMessage) event.getMessage();
                    if (m.getCommand() == ShortMessage.CONTROL_CHANGE && m.getData1() == 64)
                        events.add(new Event(event.getTick(), m.getChannel(), m.getData2()));
                } else if (event.getMessage() instanceof MetaMessage) {
                    MetaMessage m = (MetaMessage) event.getMessage();
                    if (m.getType() == 0x06 || m.getType() == 0x05) {
                        String text = new String(m.getData(), StandardCharsets.UTF_8).replaceAll("\\p{Cntrl}", " ").trim();
                        if (!text.isEmpty() && seen.add(event.getTick() + ":" + text)) marks.add(new Mark(event.getTick(), text, false));
                    }
                }
            }
        }
        events.sort(Comparator.comparingLong(e -> e.tick));
        boolean[] down = new boolean[16];
        long start = -1;
        for (Event event : events) {
            down[event.channel] = event.value >= 64;
            boolean any = false;
            for (boolean value : down) any |= value;
            if (any && start < 0) start = event.tick;
            if (!any && start >= 0) { pedals.add(new Pedal(start, event.tick)); start = -1; }
        }
        if (start >= 0) pedals.add(new Pedal(start, measures.get(measures.size() - 1).end));
    }
}
