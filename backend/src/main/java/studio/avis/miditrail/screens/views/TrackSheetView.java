package studio.avis.miditrail.screens.views;

import studio.avis.juikit.internal.JuikitView;
import studio.avis.miditrail.*;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.List;

import static studio.avis.miditrail.MIDITrail.*;

public class TrackSheetView extends TrackView {

    private static final String SYMBOL_BRACE = "\uD834\uDD14";
    private static final String SYMBOL_TREBLE_CLEFS = "\uD834\uDD1E";
    private static final String SYMBOL_BASS_CLEFS = "\uD834\uDD22";
    private static final String SYMBOL_SHARP = "\u266F";
    private static final String SYMBOL_FLAT = "\u266D";
    private static final String SYMBOL_NATURAL = "\u266E";
    private static final String SYMBOL_BLACK_NOTE_HEAD = "\uE0A4";
    private static final String SYMBOL_SEMIBREVE_NOTE_HEAD = "\uE0A2";
    private static final String SYMBOL_BREVE_NOTE_HEAD = "\uE0A0";
    private static final String SYMBOL_HALF_NOTE_HEAD = "\uE0A3";
    private static final String SYMBOL_ACCIDENTAL_SHARP = "\uE262";
    private static final String SYMBOL_ACCIDENTAL_FLAT = "\uE260";
    private static final String SYMBOL_ACCIDENTAL_NATURAL = "\uE261";
    private static final String SYMBOL_ACCIDENTAL_NATURAL_SHARP = "\uE268";
    private static final String SYMBOL_ACCIDENTAL_NATURAL_FLAT = "\uE267";

    private static final Color BACKGROUND_COLOR = Color.WHITE;
    private static final Color COMPONENTS_COLOR = Color.BLACK;
    private static final Color HIGHLIGHTS_COLOR = Color.RED;

    private static final int SCORE_WIDTH = 1250;
    private static final int SCORE_DISTANCE = 70;
    private static final int LINE_DISTANCE = 8;
    public static final int MEASURES_PER_PAGE = 5;

    private static final int[] SHARPS_SIGNATURE_INDICES = { 0, -3, 1, -2, -5, -1, -4 }; // +10 in treble, +4 in bass
    private static final int[] FLATS_SIGNATURE_INDICES = { 0, 3, -1, 2, -2, 1, -3 }; // +6 in treble, +8 in bass

    private Font musicalFont;
    private Font musicalFontText;

    private int currentBar = -1;
    private Map<Long, List<Note>> notesOnTreble = new HashMap<>();
    private Map<Long, List<Note>> notesOnBass = new HashMap<>();
    private KeySignature currentKeySignature;

    private final TrackReader reader;

    private static final AffineTransform AFFINE = new AffineTransform();

    public TrackSheetView(TrackReader reader) {
        this.reader = reader;
    }

    public enum AccidentalType {

        DEFAULT(""),
        SHARP(SYMBOL_SHARP),
        FLAT(SYMBOL_FLAT),
        NATURAL(SYMBOL_NATURAL);

        private final String symbol;

        AccidentalType(String symbol) {
            this.symbol = symbol;
        }

        public String getSymbol() {
            return symbol;
        }
    }

    public static class NoteHeadRenderQueue {

        String symbol;
        int x;
        int y;
        AccidentalType accidental;
        Color color;
        int index;
        boolean moveNoteHeadRight;
        boolean upperStem;

    }

    public int getMaximumHeight(JuikitView view) {
        return (int) (view.height() * 0.5);
    }

    @Override
    public void draw(JuikitView view, Graphics graphics) {
        long tick = view.data(TICK);
        int currentBar = getCurrentBarIndex(tick);

        boolean currentBarChanged = this.currentBar != currentBar;

        Graphics2D g = (Graphics2D) graphics;
        if(musicalFontText == null) {
            musicalFontText = view.data(MUSICAL_FONT_TEXT);
        }
        if(musicalFont == null) {
            musicalFont = view.data(MUSICAL_FONT);
        }
        int scoreFromX = (view.width() / 2) - (SCORE_WIDTH / 2);
        int scoreToX = (view.width() / 2) + (SCORE_WIDTH / 2);

        g.setColor(BACKGROUND_COLOR);
        g.fillRect(0, 0, view.width(), Math.min(getMaximumHeight(view), view.height()));

        g.setColor(COMPONENTS_COLOR);
        int yMid = getMaximumHeight(view) / 2;
        int scoreFromY = yMid - (LINE_DISTANCE * 5) - (SCORE_DISTANCE / 2);
        drawScore(view, g, scoreFromY);
        drawScore(view, g, yMid + (SCORE_DISTANCE / 2));

        int scoreToY = yMid + (SCORE_DISTANCE / 2) + (LINE_DISTANCE * 5) - LINE_DISTANCE;

        g.drawLine(
                scoreFromX, scoreFromY,
                scoreFromX, scoreToY);

        g.drawLine(
                scoreToX, scoreFromY,
                scoreToX, scoreToY);

        scoreFromX = drawSymbols(view, g, currentBar, (int) tick, scoreFromX, scoreFromY, scoreToY, yMid);
        scoreFromX += 10;

        int scoreWidth = scoreToX - scoreFromX;

        Map<Long, List<Note>> notesOnTreble;
        Map<Long, List<Note>> notesOnBass;
        if(currentBarChanged) {
            System.out.println("Notes of the page has changed.");
            notesOnTreble = new HashMap<>();
            notesOnBass = new HashMap<>();

            long fromLineTick = reader.getLines().get(currentBar - 1).getTick();
            long toLineTick = reader.getLines().get(currentBar + MEASURES_PER_PAGE - 1).getTick();

            int cMajorScale = getPlainScaleFromKey(KEY_C_MAJOR);

            Map<Integer, List<Note>> notes = reader.findNotesBetween(fromLineTick, toLineTick);
            for(List<Note> notesPerTrack : notes.values()) {
                for(Note note : notesPerTrack) {
                    long keyTick = note.getFromTick();
                    int key = getPlainScaleFromKey(note.getKey());
                    if(key >= cMajorScale) {
                        if(!notesOnTreble.containsKey(keyTick)) {
                            notesOnTreble.put(keyTick, new ArrayList<>());
                        }
                        notesOnTreble.get(keyTick).add(note);
                    } else {
                        if(!notesOnBass.containsKey(keyTick)) {
                            notesOnBass.put(keyTick, new ArrayList<>());
                        }
                        notesOnBass.get(keyTick).add(note);
                    }
                }
            }
            // in-place operations
            for(long key : notesOnTreble.keySet()) {
                notesOnTreble.get(key).sort(Comparator.comparingInt(Note::getKey));
            }
            for(long key : notesOnBass.keySet()) {
                notesOnBass.get(key).sort(Comparator.comparingInt(Note::getKey));
            }
            this.notesOnTreble = notesOnTreble;
            this.notesOnBass = notesOnBass;
        } else {
            // Caching
            notesOnTreble = this.notesOnTreble;
            notesOnBass = this.notesOnBass;
        }

        for(int i = 0; i < MEASURES_PER_PAGE; i++) {
            drawProgressBar(notesOnTreble, notesOnBass, currentBar, view, g, tick, scoreWidth, i, scoreFromX, scoreFromY, scoreToY);
        }
        this.currentBar = currentBar;

        g.dispose();
    }

    public int getCurrentBarIndex(long currentTick) {
        List<Line> lines = reader.getLines();
        for(int i = MEASURES_PER_PAGE - 1; i < lines.size(); i += MEASURES_PER_PAGE - 1) {
            int fromIndex = i - MEASURES_PER_PAGE + 1;
            Line before = lines.get(fromIndex);
            Line after = lines.get(i);

            if(before.getTick() <= currentTick && currentTick <= after.getTick()) {
                return fromIndex + 1;
            }
        }
        return lines.size() / (MEASURES_PER_PAGE - 1);
    }

    // 건반의 위치를 구하는 코드를 TrackReader 측으로 옮길 것!

    private int getPlainScaleFromKey(int key) {
        int octave = key / KEYS.length - 1;
        return octave * 7 + getScaleFromKey(key);
    }

    private int getScaleFromKey(int key) {
        return KEY_SCALE[key % KEY_SCALE.length];
    }

    private int getKeyFromPlainScale(int plainScale, int flats, int sharps) {
        int octave = plainScale / 7;
        return (octave + 1) * KEYS.length - flats + sharps;
    }

    private <T> List<T> reversed(List<T> list) {
        return new AbstractList<T>() {
            @Override
            public T get(int index) {
                return list.get(list.size() - index - 1);
            }

            @Override
            public int size() {
                return list.size();
            }
        };
    }

    private void drawProgressBar(Map<Long, List<Note>> notesOnTreble, Map<Long, List<Note>> notesOnBass, int currentBar, JuikitView view, Graphics2D g, long currentTick, int scoreWidth, int index, int scoreFromX, int scoreFromY, int scoreToY) {
        int toIndex = currentBar + index;

        Line toLine = reader.getLines().get(toIndex);

        long fromTick = reader.getLines().get(currentBar + index - 1).getTick();
        long toTick = toLine.getTick();

        boolean currentMeasure = fromTick <= currentTick && currentTick < toTick;

        int keySignatureIndex = reader.findKeySignatureBetween(fromTick, toTick);
        int timeSignatureIndex = reader.findTimeSignatureBetween(fromTick, toTick);

        int pos = scoreWidth / MEASURES_PER_PAGE * index;
        int from = scoreFromX + pos + 7;
        int diff = 0;

        KeySignature currentKey = reader.findKeySignatureBefore(toTick);
        TimeSignature currentTimeSig = reader.findTimeSignatureBefore(toTick);

        int sharps = currentKey.getSharps();
        int flats = currentKey.getFlats();

        if(keySignatureIndex > 0 && index > 0) {
            KeySignature before = reader.getKeySignatures().get(keySignatureIndex - 1);
            KeySignature after = reader.getKeySignatures().get(keySignatureIndex);

            int to = drawKeySignatureBar(view, g, before, after, from + diff);
            diff = to - from;
            diff += 5;
        }
        if(timeSignatureIndex > 0 && index > 0) {
            TimeSignature current = reader.getTimeSignatures().get(timeSignatureIndex);

            int to = drawTimeSignatureBar(view, g, current, from + diff);
            diff = to - from;
        }
        diff += 10;

        List<Integer> flatted = new ArrayList<>();
        List<Integer> sharped = new ArrayList<>();

        int fromX = scoreWidth / MEASURES_PER_PAGE * index + 5 + scoreFromX + diff;
        int toX = scoreWidth / MEASURES_PER_PAGE * (index + 1) - 5 + scoreFromX;

        int pixels = toX - fromX;
        long ticks = toTick - fromTick;

        double ratio = (double) pixels / (double) ticks;

        g.setFont(musicalFont.deriveFont((float) (LINE_DISTANCE * 4)));

        double standard = (double) currentTimeSig.getDenominator() / 4d;
        double quarterNoteCount = (double) currentTimeSig.getNumerator() / standard;

        List<NoteHeadRenderQueue> queuesOnTreble = new ArrayList<>();
        for(Map.Entry<Long, List<Note>> entry : notesOnTreble.entrySet()) {
            drawOnScore(view, g, entry.getValue(), queuesOnTreble, flatted, sharped, sharps, flats, true, currentTick, fromTick, toTick, fromX, ratio, quarterNoteCount);
        }
        for(NoteHeadRenderQueue queue : queuesOnTreble) {
            drawNote(g, queue.symbol, queue.index, queue.x, queue.y, queue.accidental, queue.color, queue.moveNoteHeadRight, queue.upperStem);
        }

        List<NoteHeadRenderQueue> queuesOnBass = new ArrayList<>();
        for(Map.Entry<Long, List<Note>> entry : notesOnBass.entrySet()) {
            drawOnScore(view, g, entry.getValue(), queuesOnBass, flatted, sharped, sharps, flats, false, currentTick, fromTick, toTick, fromX, ratio, quarterNoteCount);
        }
        for(NoteHeadRenderQueue queue : queuesOnBass) {
            drawNote(g, queue.symbol, queue.index, queue.x, queue.y, queue.accidental, queue.color, queue.moveNoteHeadRight, queue.upperStem);
        }

        g.setColor(COMPONENTS_COLOR);

        if(index < MEASURES_PER_PAGE - 1) {
            if(currentMeasure) {
                long progress = currentTick - fromTick;

                int x = (int) (fromX + (progress * ratio));

                g.drawLine(x, 0, x, getMaximumHeight(view));
            }
        }
        if(index > 0) {
            int x = scoreWidth / MEASURES_PER_PAGE * index;
            if(toLine.isFinale()) {
                drawBar(g, scoreFromX + x - 2, scoreFromY, scoreToY);
                g.fillRect(scoreFromX + x + 1, scoreFromY, 3, scoreToY - scoreFromY);
            } else if(keySignatureIndex != -1 || timeSignatureIndex != -1) {
                drawBar(g, scoreFromX + x - 2, scoreFromY, scoreToY);
                drawBar(g, scoreFromX + x + 2, scoreFromY, scoreToY);
            } else {
                drawBar(g, scoreFromX + x, scoreFromY, scoreToY);
            }
        }
    }

    private boolean needsUpperStem(List<Note> noteList, int cMajorScale) {
        long sumOfScale = 0;
        boolean greaterThanCMajor = false;
        for(Note note : noteList) {
            int key = note.getKey();
            if(key >= KEY_C_MAJOR) {
                greaterThanCMajor = true;
                sumOfScale += getPlainScaleFromKey(key) - cMajorScale;
            } else {
                greaterThanCMajor = false;
                sumOfScale += cMajorScale - getPlainScaleFromKey(key);
            }
        }
        long averageKey = sumOfScale / noteList.size();
        if(greaterThanCMajor) {
            return averageKey < 6;
        } else {
            return averageKey >= 6;
        }
    }

    private void drawBar(Graphics2D g, int x, int fromY, int toY) {
        g.drawLine(x, fromY, x, toY);
    }

    private int drawKeySignatureBar(JuikitView view, Graphics2D g, KeySignature before, KeySignature after, int scoreFromX) {
        g.setFont(musicalFont.deriveFont(24f));

        int beforeSharps = before == null ? 0 : before.getSharps();
        int afterSharps = after.getSharps();
        int beforeFlats = before == null ? 0 : before.getFlats();
        int afterFlats = after.getFlats();

        if(beforeSharps > afterSharps) {
            for(int i = afterSharps; i < beforeSharps; i++) {
                int index = SHARPS_SIGNATURE_INDICES[i];

                g.drawString(SYMBOL_NATURAL, scoreFromX, getNoteHeight(view, 10 + index, true));
                g.drawString(SYMBOL_NATURAL, scoreFromX, getNoteHeight(view, 4 - index, false));

                scoreFromX += 5;
            }
        }

        if(beforeFlats > afterFlats) {
            for(int i = afterFlats; i < beforeFlats; i++) {
                int index = FLATS_SIGNATURE_INDICES[i];

                g.drawString(SYMBOL_NATURAL, scoreFromX, getNoteHeight(view, 6 + index, true));
                g.drawString(SYMBOL_NATURAL, scoreFromX, getNoteHeight(view, 8 - index, false));

                scoreFromX += 5;
            }
        }

        if(beforeSharps < afterSharps) {
            for(int i = beforeSharps; i < afterSharps; i++) {
                int index = SHARPS_SIGNATURE_INDICES[i];

                g.drawString(SYMBOL_SHARP, scoreFromX, getNoteHeight(view, 10 + index, true));
                g.drawString(SYMBOL_SHARP, scoreFromX, getNoteHeight(view, 4 - index, false));

                scoreFromX += 5;
            }
        }

        if(beforeFlats < afterFlats) {
            for(int i = beforeFlats; i < afterFlats; i++) {
                int index = FLATS_SIGNATURE_INDICES[i];

                g.drawString(SYMBOL_FLAT, scoreFromX, getNoteHeight(view, 6 + index, true));
                g.drawString(SYMBOL_FLAT, scoreFromX, getNoteHeight(view, 8 - index, false));

                scoreFromX += 5;
            }
        }

        return scoreFromX;
    }

    private int drawKeySignature(int currentBar, JuikitView view, Graphics2D g, int scoreFromX) {
        // Key Signature
        scoreFromX += 25;

        Line line = reader.getLines().get(currentBar - 1);

        KeySignature last = null;
        for(KeySignature signature : reader.getKeySignatures()) {
            if(signature.getTick() > line.getTick()) {
                break;
            }
            if(signature.getTick() <= line.getTick()) {
                last = signature;
            }
        }
        if(last == null) {
            last = reader.getKeySignatures().get(0);
        }

        currentKeySignature = last;

        return drawKeySignatureBar(view, g, null, last, scoreFromX);
    }

    private int drawTimeSignatureBar(JuikitView view, Graphics2D g, TimeSignature current, int scoreFromX) {
        g.setFont(musicalFont.deriveFont((float) (LINE_DISTANCE * 4)));
        FontMetrics metrics = g.getFontMetrics();

        int numeratorWidth = metrics.stringWidth(createTimeSigSymbol(current.getNumerator()));
        int denominatorWidth = metrics.stringWidth(createTimeSigSymbol(current.getDenominator()));

        int baseWidth = Math.max(numeratorWidth, denominatorWidth);

        g.drawString(createTimeSigSymbol(current.getNumerator()), scoreFromX + (baseWidth / 2) - (numeratorWidth / 2), getNoteHeight(view, 8, true));
        g.drawString(createTimeSigSymbol(current.getDenominator()), scoreFromX + (baseWidth / 2) - (denominatorWidth / 2), getNoteHeight(view, 4, true));

        g.drawString(createTimeSigSymbol(current.getNumerator()), scoreFromX + (baseWidth / 2) - (numeratorWidth / 2), getNoteHeight(view, 4, false));
        g.drawString(createTimeSigSymbol(current.getDenominator()), scoreFromX + (baseWidth / 2) - (denominatorWidth / 2), getNoteHeight(view, 8, false));

        scoreFromX += baseWidth;

        return scoreFromX;
    }

    private int drawTimeSignature(int currentBar, JuikitView view, Graphics2D g, int scoreFromX) {
        // Time Signature
        scoreFromX += 5;

        Line line = reader.getLines().get(currentBar - 1);

        TimeSignature last = null;
        for(TimeSignature signature : reader.getTimeSignatures()) {
            if(signature.getTick() > line.getTick()) {
                break;
            }
            if(signature.getTick() <= line.getTick()) {
                last = signature;
            }
        }
        if(last == null) {
            last = reader.getTimeSignatures().get(0);
        }

        return drawTimeSignatureBar(view, g, last, scoreFromX);
    }

    private int drawSymbols(JuikitView view, Graphics2D g, int currentBar, int currentTick, int scoreFromX, int scoreFromY, int scoreToY, int yMid) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Brace
        g.setFont(musicalFont.deriveFont((float) (scoreToY - scoreFromY)));
        g.drawString(SYMBOL_BRACE, scoreFromX - 13, scoreToY);

        // Clefs
        scoreFromX += 5;
        g.setFont(musicalFont.deriveFont(30f));
        g.drawString(SYMBOL_TREBLE_CLEFS, scoreFromX, yMid - (SCORE_DISTANCE / 2) - (LINE_DISTANCE * 5 / 2) + 3); // Treble
        g.drawString(SYMBOL_BASS_CLEFS, scoreFromX, yMid + (SCORE_DISTANCE / 2) + 8); // Bass

        scoreFromX = drawKeySignature(currentBar, view, g, scoreFromX);
        scoreFromX = drawTimeSignature(currentBar, view, g, scoreFromX);

        return scoreFromX;
    }

    private String createTimeSigSymbol(int value) {
        if(value >= 10) {
            int front = value / 10;
            int remaining = value % (front * 10);
            return createTimeSigSymbol(front) + createTimeSigSymbol(remaining);
        } else {
            return String.valueOf((char) (value + '\uE080'));
        }
    }

    private void drawScore(JuikitView view, Graphics graphics, int y) {
        for(int i = 0; i < 5; i++) {
            int scoreY = y + (i * LINE_DISTANCE);
            graphics.drawLine(
                    (view.width() / 2) - (SCORE_WIDTH / 2), scoreY,
                    (view.width() / 2) + (SCORE_WIDTH / 2), scoreY);
        }
    }

    // index 0 means 1 octave C
    private int getNoteHeight(JuikitView view, int index, boolean treble) {
        int yMid = getMaximumHeight(view) / 2;
        if(treble) {
            int mid = yMid - (SCORE_DISTANCE / 2);
            return mid - (index * (LINE_DISTANCE / 2));
        } else {
            int mid = yMid + (SCORE_DISTANCE / 2) - LINE_DISTANCE;
            return mid + (index * (LINE_DISTANCE / 2));
        }
    }

    private void drawTie(Graphics2D g, int fromX, int toX, int y, boolean up) {
        int width = fromX - toX;
        if(width <= 0) return;

        g.drawArc(fromX, y, fromX - toX, 10, 0, -270);
        System.out.println("size: " + (fromX - toX));

//        String symbol = up ? SYMBOL_TIE_UP : SYMBOL_TIE_DOWN;
//        Font currentFont = g.getFont();
//
//        float currentFontSize = 12f;
//        float desiredFontSize = toX - fromX;
//
//        g.setFont(musicalFontText.deriveFont(currentFontSize));
//        AFFINE.scale(50, 1d);
//        g.setTransform(AFFINE);
//        g.drawString("\uE1FD", toX, y);
//        AFFINE.setToIdentity();
//        g.setTransform(AFFINE);
//        g.setFont(currentFont);
    }

    private NoteHeadRenderQueue drawNoteSymbol(String symbol, JuikitView view, Graphics g, int x, int index, boolean treble, boolean currentMeasure, AccidentalType accidentalType, boolean moveNoteHeadRight, boolean upperStem) {
        Color storedColor = g.getColor();

        g.setColor(COMPONENTS_COLOR);
        if(index < 2) {
            for(int i = 0; i >= index; i--) {
                if(i % 2 == 0) {
                    int lineHeight = getNoteHeight(view, i, treble);
                    g.drawLine(x - 3, lineHeight, x + 13, lineHeight);
                }
            }
        } else if(index > 10) {
            for(int i = 12; i <= index; i++) {
                if(i % 2 == 0) {
                    int lineHeight = getNoteHeight(view, i, treble);
                    g.drawLine(x - 3, lineHeight, x + 13, lineHeight);
                }
            }
        }

        if(currentMeasure) {
            NoteHeadRenderQueue queue = new NoteHeadRenderQueue();
            queue.symbol = symbol;
            queue.x = x;
            queue.y = getNoteHeight(view, index, treble);
            queue.accidental = accidentalType;
            queue.color = storedColor;
            queue.index = index;
            queue.moveNoteHeadRight = moveNoteHeadRight;
            queue.upperStem = upperStem;
            return queue;
        } else {
            drawNote(g, symbol, index, x, getNoteHeight(view, index, treble), accidentalType, storedColor, moveNoteHeadRight, upperStem);
        }
        return null;
    }

    private void drawNote(Graphics g, String symbol, int index, int x, int y, AccidentalType accidental, Color color, boolean moveNoteHeadRight, boolean upperStem) {
        boolean isBreve = symbol.equals(SYMBOL_BREVE_NOTE_HEAD) || symbol.equals(SYMBOL_SEMIBREVE_NOTE_HEAD);

        int symbolPosX = isBreve ? x - 1 : x;
        if(upperStem) {
            symbolPosX += moveNoteHeadRight ? 9 : 0;
        } else {
            symbolPosX -= moveNoteHeadRight ? 0 : 9;
        }
        if(accidental != AccidentalType.DEFAULT) {
            g.setColor(COMPONENTS_COLOR);
            g.drawString(accidental.getSymbol(), symbolPosX - 10, y);
        }
        g.setColor(color);
        if(isBreve) {
            g.setFont(musicalFont.deriveFont((float) (LINE_DISTANCE * 4) - 4f));
        }
        g.drawString(symbol, symbolPosX, y);
        if(isBreve) {
            g.setFont(musicalFont.deriveFont((float) (LINE_DISTANCE * 4)));
        } else {
            if(upperStem) {
                g.drawLine(x + 9, y - 30, x + 9, y);
            } else {
                g.drawLine(x, y, x, y + 30);
            }
        }
    }

    private void drawOnScore(
            JuikitView view,
            Graphics g,
            List<Note> notes,
            List<NoteHeadRenderQueue> queues,
            List<Integer> flatted,
            List<Integer> sharped,
            int currentSharps,
            int currentFlats,
            boolean treble,
            long currentTick,
            long fromTick,
            long toTick,
            int fromX,
            double ratio,
            double quarterNoteCount) {

        int prevScale = -9999;

        boolean currentMeasure = fromTick <= currentTick && currentTick < toTick;
        long measureLength = toTick - fromTick;
        int cMajorScale = getPlainScaleFromKey(KEY_C_MAJOR);
        boolean upperStem = needsUpperStem(notes, cMajorScale);
        boolean moveNoteHeadRight = !upperStem;

        for(Note note : (treble ? notes : reversed(notes))) {
            long startNoteTick = note.getFromTick();
            long endNoteTick = note.getEndTick();
            if(fromTick > startNoteTick || endNoteTick >= toTick) {
                continue;
            }

            long pos = startNoteTick - fromTick;

            int x = (int) (fromX + (pos * ratio));
            int key = note.getKey();
            g.setColor(startNoteTick <= currentTick && currentTick < endNoteTick ? HIGHLIGHTS_COLOR : COMPONENTS_COLOR);

            int keyScale = getPlainScaleFromKey(key);
            int relativePos = key >= KEY_C_MAJOR ? keyScale - cMajorScale : cMajorScale - keyScale;
            int keyScaleDiff = Math.abs(relativePos - prevScale);
            if (prevScale == -9999) {
                prevScale = relativePos;
            } else if (keyScaleDiff > 1) {
                prevScale = -9999;
                moveNoteHeadRight = !upperStem;
            } else if (keyScaleDiff >= 0) {
                prevScale = relativePos;
                moveNoteHeadRight = !moveNoteHeadRight;
            }

            AccidentalType accidentalType = AccidentalType.DEFAULT;
            if (note.isSharp()) {
                if (currentFlats > 0) {
                    keyScale += 1;
                    if (!flatted.contains(keyScale)) {
                        accidentalType = AccidentalType.FLAT;
                        flatted.add(keyScale);
                    }
                } else {
                    if (!sharped.contains(keyScale)) {
                        accidentalType = AccidentalType.SHARP;
                        sharped.add(keyScale);
                    }
                }
            } else {
                if (sharped.contains(keyScale)) {
                    sharped.remove((Integer) keyScale);
                    accidentalType = AccidentalType.NATURAL;
                }
                if (flatted.contains(keyScale)) {
                    flatted.remove((Integer) keyScale);
                    accidentalType = AccidentalType.NATURAL;
                }
            }

            String symbol;
            long noteLength = endNoteTick - startNoteTick + 1;
            long quarterNoteLength = (long) (measureLength / quarterNoteCount);
            if (noteLength >= quarterNoteLength * 4) {
                // 1
                symbol = SYMBOL_SEMIBREVE_NOTE_HEAD;
            } else if (noteLength >= quarterNoteLength * 2) {
                // 2
                symbol = SYMBOL_HALF_NOTE_HEAD;
            } else {
                // 4, 8, 16 ...
                symbol = SYMBOL_BLACK_NOTE_HEAD;
            }

            int noteVerticalPos;
            if(treble) {
                noteVerticalPos = keyScale - cMajorScale;
            } else {
                noteVerticalPos = cMajorScale - keyScale;
            }
            NoteHeadRenderQueue queue = drawNoteSymbol(symbol, view, g, x, noteVerticalPos, treble, currentMeasure, accidentalType, moveNoteHeadRight, upperStem);
            if(queue != null) {
                queues.add(queue);
            }
        }
    }
}
