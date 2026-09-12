package studio.avis.miditrail.sheet;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import studio.avis.miditrail.*;
import javax.sound.midi.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import static org.junit.Assert.*;

public class ScoreTest {
    @Rule public TemporaryFolder files = new TemporaryFolder();

    private static void meta(Track track, int type, long tick, int... values) throws Exception {
        byte[] data = new byte[values.length];
        for (int i = 0; i < data.length; i++) data[i] = (byte) values[i];
        MetaMessage message = new MetaMessage(); message.setMessage(type, data, data.length);
        track.add(new MidiEvent(message, tick));
    }
    private static void event(Track track, long tick, int command, int channel, int a, int b) throws Exception {
        ShortMessage message = new ShortMessage(); message.setMessage(command, channel, a, b);
        track.add(new MidiEvent(message, tick));
    }
    private static void note(Track track, long start, long duration, int key) throws Exception {
        event(track, start, ShortMessage.NOTE_ON, 0, key, 100);
        event(track, start + duration, ShortMessage.NOTE_OFF, 0, key, 0);
    }
    private TrackReader read(Sequence sequence) throws Exception {
        File file = files.newFile("fixture-" + System.nanoTime() + ".mid");
        MidiSystem.write(sequence, 1, file);
        TrackReader reader = new TrackReader(file); reader.loadTrack(); return reader;
    }
    private List<Score.Head> heads(Score score, long tick, int staff) {
        List<Score.Head> result = new ArrayList<>();
        for (Score.Chord chord : score.measures.get(score.measureAt(tick)).chords)
            if (chord.start == tick && chord.staff == staff) result.addAll(chord.heads);
        return result;
    }
    private Score.Chord chord(Score score, long tick, int staff) {
        for (Score.Chord chord : score.measures.get(score.measureAt(tick)).chords)
            if (!chord.rest && chord.start == tick && chord.staff == staff) return chord;
        throw new AssertionError("Missing chord " + tick);
    }

    @Test public void pairsChannelsRepeatedPitchesAndChannelScopedAllNotesOff() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480); Track track = sequence.createTrack();
        event(track, 0, ShortMessage.NOTE_ON, 0, 60, 80);
        event(track, 5, ShortMessage.NOTE_ON, 1, 60, 80);
        event(track, 10, ShortMessage.NOTE_ON, 0, 60, 80);
        event(track, 20, ShortMessage.NOTE_ON, 0, 60, 0);
        event(track, 30, ShortMessage.CONTROL_CHANGE, 0, 123, 0);
        event(track, 40, ShortMessage.NOTE_OFF, 1, 60, 0);
        event(track, 50, ShortMessage.NOTE_ON, 2, 64, 80);
        meta(track, 0x2f, 70);
        TrackReader reader = read(sequence);
        List<Note> notes = reader.getTracksForPianoRoll().get(0);
        assertEquals(4, notes.size());
        assertEquals(20, notes.get(0).getEndTick());
        assertEquals(40, notes.get(1).getEndTick());
        assertEquals(30, notes.get(2).getEndTick());
        assertEquals(70, notes.get(3).getEndTick());
        reader.loadTrack(); assertEquals(4, reader.getTracksForPianoRoll().get(0).size());
        assertEquals(2, reader.getLines().size());
    }

    @Test public void discoversMetadataOutsideConductorTrackAndHandlesPartialFinalBar() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480); sequence.createTrack(); Track track = sequence.createTrack();
        meta(track, 0x58, 960, 3, 3, 24, 8); // mid-bar 3/8 change
        meta(track, 0x59, 960, -3, 1);
        meta(track, 0x51, 960, 0x06, 0x1a, 0x80);
        note(track, 0, 1680, 60);
        Track padding = sequence.createTrack(); meta(padding, 0x2f, 100000);
        TrackReader reader = read(sequence);
        assertEquals(Arrays.asList(0L, 960L, 1680L), Arrays.asList(reader.getLines().get(0).getTick(), reader.getLines().get(1).getTick(), reader.getLines().get(2).getTick()));
        assertEquals(4, reader.findTimeSignatureBefore(0).getNumerator());
        assertEquals(3, reader.findTimeSignatureBefore(960).getNumerator());
        assertEquals(3, reader.findKeySignatureBefore(960).getFlats());
        assertEquals(2, reader.getTemps().size());
        assertTrue(reader.getLines().get(2).isFinale());
    }

    @Test public void crossingBarAndBeatPreservesEveryTickAndLinksTies() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480); Track track = sequence.createTrack();
        note(track, 1680, 960, 60);
        Score score = new Score(read(sequence));
        Score.Head first = heads(score, 1680, 0).get(0), next = heads(score, 1920, 0).get(0);
        assertFalse(first.tieIn); assertTrue(first.tieOut);
        assertTrue(next.tieIn); assertFalse(next.tieOut);
        assertSame(first.source, next.source);
        assertEquals(1920, chord(score, 1680, 0).end);
        assertEquals(2640, chord(score, 1920, 0).end);
        assertEquals(8, chord(score, 1680, 0).rhythm.denominator);
        long sum = 0;
        for (Score.Measure m : score.measures) for (Score.Chord c : m.chords) for (Score.Head h : c.heads) sum += c.end - c.start;
        assertEquals(960, sum);
    }

    @Test public void accidentalsAreChronologicalOctaveAndStaffScopedWithKeyDefaults() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480); Track track = sequence.createTrack();
        meta(track, 0x59, 0, 1, 0); // G major, F sharp
        note(track, 0, 240, 66); note(track, 240, 240, 65); note(track, 480, 240, 66);
        note(track, 720, 240, 78); // F#5 remains supplied by signature
        note(track, 960, 240, 54); // F#3 independent staff
        note(track, 1920, 240, 66); // reset at bar
        Score score = new Score(read(sequence));
        assertEquals(Score.NONE, heads(score, 0, 0).get(0).accidental);
        assertEquals(0, heads(score, 240, 0).get(0).accidental);
        assertEquals(1, heads(score, 480, 0).get(0).accidental);
        assertEquals(Score.NONE, heads(score, 720, 0).get(0).accidental);
        assertEquals(Score.NONE, heads(score, 960, 1).get(0).accidental);
        assertEquals(Score.NONE, heads(score, 1920, 0).get(0).accidental);
    }

    @Test public void flatKeysAndEnharmonicBoundaryPitchesUseCorrectStaffSteps() {
        KeySignature df = new KeySignature(0, KeySignature.Gender.MAJOR, 2);
        Score.Pitch db = Score.spell(61, df);
        assertEquals(29, db.step); assertEquals(-1, db.alteration);
        Score.Pitch cb = Score.spell(59, new KeySignature(0, KeySignature.Gender.MAJOR, 0));
        assertEquals(28, cb.step); assertEquals(-1, cb.alteration);
        Score.Pitch es = Score.spell(65, new KeySignature(0, KeySignature.Gender.MAJOR, 13));
        assertEquals(30, es.step); assertEquals(1, es.alteration);
    }

    @Test public void tiedAccidentalDoesNotAlterLaterAttacksInTheNextBar() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480); Track track = sequence.createTrack();
        note(track, 1680, 480, 61);
        note(track, 2400, 240, 61);
        Score score = new Score(read(sequence));
        assertEquals(1, heads(score, 1680, 0).get(0).accidental);
        assertEquals(Score.NONE, heads(score, 1920, 0).get(0).accidental);
        assertEquals(1, heads(score, 2400, 0).get(0).accidental);
    }

    @Test public void ordinaryCompositeDurationsAndRestsDoNotBecomeTuplets() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480); Track track = sequence.createTrack();
        note(track, 0, 1200, 60); // five eighths: half plus eighth, tied
        note(track, 1920, 720, 60); // leaves five eighths of silence
        Score score = new Score(read(sequence));
        assertEquals(2, chord(score, 0, 0).rhythm.denominator);
        assertEquals(8, chord(score, 960, 0).rhythm.denominator);
        for (Score.Measure m : score.measures) for (Score.Chord c : m.chords) assertEquals(0, c.rhythm.tuplet);
    }

    @Test public void accidentalStateResetsAtAnInMeasureKeyChange() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480); Track track = sequence.createTrack();
        note(track, 0, 240, 66);
        meta(track, 0x59, 480, 1, 0);
        note(track, 480, 240, 66); note(track, 720, 240, 65);
        Score score = new Score(read(sequence));
        assertEquals(1, heads(score, 0, 0).get(0).accidental);
        assertEquals(Score.NONE, heads(score, 480, 0).get(0).accidental);
        assertEquals(0, heads(score, 720, 0).get(0).accidental);
    }

    @Test public void retainsVoicesDotsTripletsAndSubQuarterBeamLevels() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480); Track track = sequence.createTrack();
        note(track, 0, 720, 72); note(track, 0, 120, 60); note(track, 120, 60, 62);
        for (int i = 0; i < 3; i++) note(track, 960 + i * 160, 160, 64 + i);
        Score score = new Score(read(sequence));
        assertEquals(1, chord(score, 0, 0).rhythm.dots);
        assertEquals(4, chord(score, 0, 0).rhythm.denominator);
        List<Score.Chord> simultaneous = new ArrayList<>();
        for (Score.Chord c : score.measures.get(0).chords) if (c.start == 0 && c.staff == 0 && !c.rest) simultaneous.add(c);
        assertEquals(2, simultaneous.size()); assertNotEquals(simultaneous.get(0).voice, simultaneous.get(1).voice);
        assertEquals(3, chord(score, 120, 0).rhythm.beams());
        for (int i = 0; i < 3; i++) {
            assertEquals(3, chord(score, 960 + i * 160, 0).rhythm.tuplet);
            assertEquals(8, chord(score, 960 + i * 160, 0).rhythm.denominator);
        }
    }

    @Test public void emptyShortAndFinalPagesRenderWithoutPaddingOrMutatingGraphics() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480); sequence.createTrack();
        SheetRenderer renderer = new SheetRenderer(read(sequence));
        assertEquals(1, renderer.getScore().measures.size());
        assertEquals(2, renderer.getScore().measures.get(0).chords.size());
        for (Score.Chord c : renderer.getScore().measures.get(0).chords) assertTrue(c.rest && c.wholeRest);
        BufferedImage image = new BufferedImage(1000, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); g.translate(3, 7); g.setColor(Color.MAGENTA);
        AffineTransform transform = g.getTransform();
        renderer.paint(g, 900, 500, 0);
        renderer.paint(g, 900, 500, Long.MAX_VALUE);
        assertEquals(transform, g.getTransform()); assertEquals(Color.MAGENTA, g.getColor());
        assertEquals(1, renderer.getBuildCount());
        renderer.paint(g, 800, 400, 0); assertEquals(2, renderer.getBuildCount());
        g.dispose();
    }

    @Test public void isolatedShortestFlagsHaveEnoughStemClearance() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480); Track track = sequence.createTrack();
        note(track, 0, 8, 96);
        SheetRenderer renderer = new SheetRenderer(read(sequence));
        BufferedImage image = new BufferedImage(1000, 500, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); renderer.paint(g, 1000, 500, 0); g.dispose();
        SheetRenderer.Placed note = renderer.placed.stream().filter(p -> !p.chord.rest).findFirst().get();
        assertEquals(6, note.chord.rhythm.beams()); assertFalse(note.beamed);
        assertTrue("All six flags must clear the notehead", note.high - note.stemEnd >= 23 + 5 * 5 + 3);
    }

    @Test public void readsPedalIntervalsAcrossChannelsAndAvoidsSmpteMistranscription() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480); Track track = sequence.createTrack(); note(track, 0, 3000, 60);
        event(track, 10, ShortMessage.CONTROL_CHANGE, 0, 64, 127);
        event(track, 20, ShortMessage.CONTROL_CHANGE, 1, 64, 127);
        event(track, 30, ShortMessage.CONTROL_CHANGE, 0, 64, 0);
        event(track, 40, ShortMessage.CONTROL_CHANGE, 1, 64, 0);
        Score score = new Score(read(sequence)); assertEquals(1, score.pedals.size());
        assertEquals(10, score.pedals.get(0).start); assertEquals(40, score.pedals.get(0).end);
        Sequence smpte = new Sequence(Sequence.SMPTE_25, 40); note(smpte.createTrack(), 0, 1000, 60);
        assertFalse(new Score(read(smpte)).metrical);
    }

    @Test public void sampleCoverageSeeksAndCachedPlayback() throws Exception {
        File sample = new File("../sample-midi/Night of Nights.mid");
        assertTrue("Run Maven from the repository root or backend module", sample.isFile());
        TrackReader reader = new TrackReader(sample); reader.loadTrack();
        assertEquals(17084, reader.getTracksForPianoRoll().values().stream().mapToInt(List::size).sum());
        SheetRenderer renderer = new SheetRenderer(reader); Score score = renderer.getScore();
        assertEquals(157, score.measures.size());
        Map<String, Long> written = new HashMap<>(), expected = new HashMap<>(), notated = new HashMap<>();
        for (List<Note> track : reader.getTracksForSheet().values()) for (Note note : track) expected.put(id(note), note.getEndTick() - note.getFromTick());
        for (Score.Measure m : score.measures) for (Score.Chord c : m.chords) {
            assertTrue(c.start >= m.start); assertTrue(c.end <= m.end); assertTrue(c.end > c.start);
            for (Score.Head h : c.heads) {
                written.merge(id(h.source), c.end - c.start, Long::sum);
                notated.put(id(h.source), h.notatedEnd - h.source.getFromTick());
                if (h.source.getEndTick() - h.source.getFromTick() > reader.getSequence().getResolution() * 4L)
                    assertEquals("Long sustained notes must keep their full duration", h.source.getEndTick(), h.notatedEnd);
            }
        }
        assertEquals("Every distinct MIDI note must remain represented", expected.keySet(), written.keySet());
        assertEquals("Written fragments must cover their display duration exactly", notated, written);
        BufferedImage image = new BufferedImage(1600, 650, BufferedImage.TYPE_INT_RGB); Graphics2D g = image.createGraphics();
        for (int p = score.firstSoundingMeasure; p < score.measures.size(); p += 4) renderer.paint(g, 1600, 650, score.measures.get(p).start);
        assertEquals(39, renderer.getBuildCount());
        renderer.paint(g, 1600, 650, Long.MAX_VALUE); assertEquals(39, renderer.getBuildCount());
        renderer.paint(g, 1600, 650, 238100); int count = renderer.getBuildCount();
        for (int i = 0; i < 120; i++) renderer.paint(g, 1600, 650, 238100 + i);
        assertEquals(count, renderer.getBuildCount());
        g.dispose();
    }
    private static String id(Note note) { return note.getKey() + ":" + note.getFromTick() + ":" + note.getEndTick(); }

    @Test public void openingMatchesReferenceChordRhythmsAndStaffSpecificSpelling() throws Exception {
        TrackReader reader = new TrackReader(new File("../sample-midi/Night of Nights.mid")); reader.loadTrack();
        Score score = new Score(reader);
        assertEquals(1, score.firstSoundingMeasure);
        Score.Chord treble = chord(score, 1920, 0), bass = chord(score, 1920, 1);
        assertEquals(10, treble.heads.size()); assertEquals(8, treble.rhythm.denominator);
        assertEquals(9, bass.heads.size()); assertEquals(4, bass.rhythm.denominator);
        assertEquals(8, chord(score, 2520, 0).rhythm.denominator);
        assertEquals(0, chord(score, 2520, 0).rhythm.tuplet);
        Score.Head cs = heads(score, 5520, 0).get(0), db = heads(score, 5520, 1).get(0);
        assertEquals(61, cs.source.getKey()); assertEquals(28, cs.pitch.step); assertEquals(1, cs.accidental);
        assertEquals(25, db.source.getKey()); assertEquals(8, db.pitch.step); assertEquals(-1, db.accidental);
        List<Score.Chord> rests = new ArrayList<>();
        for (Score.Chord c : score.measures.get(1).chords) if (c.rest && c.staff == 1) rests.add(c);
        assertEquals(2, rests.size()); assertEquals(2400, rests.get(0).start); assertEquals(4, rests.get(0).rhythm.denominator);
        assertEquals(2880, rests.get(1).start); assertEquals(2, rests.get(1).rhythm.denominator);
        // Engraving gate normalization must not lengthen or shorten the MIDI data used for audio.
        assertTrue(reader.getTracksForPianoRoll().get(2).stream().anyMatch(n -> n.getFromTick() == 2520 && n.getEndTick() == 2664));
        SheetRenderer renderer = new SheetRenderer(reader);
        BufferedImage image = new BufferedImage(1600, 650, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); renderer.paint(g, 1600, 650, 0); g.dispose();
        for (SheetRenderer.Placed p : renderer.placed) {
            if (p.chord.rest) continue;
            assertEquals("The first head must sit on the normal side of its stem", p.x, p.headX.get(0), .01);
            if (p.chord.staff == 0) {
                assertTrue("Reference treble stems point upward", p.up);
                assertTrue(p.stemX > p.headX.get(0));
            } else if (p.beamed) {
                assertFalse("Reference bass beams lie below the notes", p.up);
                assertTrue(p.stemX < p.headX.get(0));
            }
        }
    }

    @Test public void exportNotationFixtureForVisualReview() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480); Track track = sequence.createTrack();
        meta(track, 0x59, 0, 1, 0);
        note(track, 0, 720, 66); note(track, 720, 240, 65);
        for (int i = 0; i < 3; i++) note(track, 960 + i * 160, 160, 67 + i);
        note(track, 1440, 120, 72); note(track, 1560, 60, 74); note(track, 1620, 60, 76);
        note(track, 1680, 960, 78);
        note(track, 0, 1920, 43); note(track, 1920, 960, 48);
        meta(track, 0x59, 3840, -3, 0); meta(track, 0x58, 3840, 6, 3, 24, 8);
        for (int i = 0; i < 6; i++) note(track, 3840 + i * 240, 240, new int[]{63,65,67,68,70,72}[i]);
        event(track, 1600, ShortMessage.CONTROL_CHANGE, 0, 64, 127);
        event(track, 4600, ShortMessage.CONTROL_CHANGE, 0, 64, 0);
        note(track, 5280, 960, 60); note(track, 5280, 960, 64); note(track, 5280, 960, 67);
        SheetRenderer renderer = new SheetRenderer(read(sequence));
        BufferedImage image = new BufferedImage(1600, 650, BufferedImage.TYPE_INT_RGB); Graphics2D g = image.createGraphics();
        renderer.paint(g, 1600, 650, 1700); g.dispose();
        File output = new File("target/sheet-previews/notation-fixture.png"); output.getParentFile().mkdirs(); ImageIO.write(image, "png", output);
    }
}
