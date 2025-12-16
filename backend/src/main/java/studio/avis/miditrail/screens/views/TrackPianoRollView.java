package studio.avis.miditrail.screens.views;

import studio.avis.juikit.internal.JuikitView;
import studio.avis.miditrail.Line;
import studio.avis.miditrail.Note;
import studio.avis.miditrail.Temp;
import studio.avis.miditrail.TrackReader;
import studio.avis.miditrail.screens.ScreenManager;
import studio.avis.miditrail.screens.TrackScreen;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static studio.avis.miditrail.MIDITrail.*;

public class TrackPianoRollView extends TrackView {

    public static final int KEYBOARD_HEIGHT = 70;
    public static final int SHARP_HEIGHT = 40;
    public static final int NORMAL_HEIGHT = KEYBOARD_HEIGHT - SHARP_HEIGHT;
    public static final int PIANO_MIN = 21;
    public static final int PIANO_MAX = 108;

    private static final Color[] COLORS = {
            new Color(135, 0, 0),
            new Color(135, 104, 0),
            new Color(135, 135, 0),
            new Color(0, 135, 0),
            new Color(0, 0, 135),
            new Color(0, 135, 135),
            new Color(135, 88, 88),
            new Color(135, 0, 135)
    };

    private static final Color[] PRESSED_COLORS = {
            new Color(205, 0, 0),
            new Color(205, 160, 0),
            new Color(205, 205, 0),
            new Color(0, 205, 0),
            new Color(0, 0, 205),
            new Color(0, 205, 205),
            new Color(205, 140, 140),
            new Color(205, 0, 205)
    };

    private final TrackReader reader;
    private final TrackScreen trackScreen;
    private final ScreenManager screenManager;

    public TrackPianoRollView(TrackReader reader, TrackScreen trackScreen, ScreenManager screenManager) {
        this.reader = reader;
        this.trackScreen = trackScreen;
        this.screenManager = screenManager;
    }

    public int getPianoRollHorizontalStartPoint(JuikitView view) {
        return (int) (view.height() * 0.5);
    }

    @Override
    public void draw(JuikitView view, Graphics graphics) {
        graphics.setColor(Color.BLACK);
        graphics.fillRect(0, getPianoRollHorizontalStartPoint(view), view.width(), view.height());

        int indent = (view.width() - NOTE_WIDTH * 127) / 2;
        int height = view.height() - view.data(ADDITIONAL_HEIGHT, int.class);
        int scroll = view.data(SCROLL);
        double multiply = view.data(MULTIPLY);
        String formattedMultiply = view.data(MULTIPLY_FORMATTED);

        graphics.setColor(Color.BLACK);
        graphics.fillRect(0, getPianoRollHorizontalStartPoint(view), view.width(), view.height());

        int noteCount = 0;

        java.util.List<Note> pressedNotes = new ArrayList<>();
        for(Map.Entry<Integer, java.util.List<Note>> entry : reader.getTracksForPianoRoll().entrySet()) {
            int trackId = entry.getKey();

            java.util.List<Note> notes = entry.getValue();
            for(int i = 0; i < notes.size(); i++) {
                Note note = notes.get(i);

                long fromTick = note.getFromTick();
                long endTick = note.getEndTick();

                fromTick /= multiply;
                endTick /= multiply;

                long fromInWindow = fromTick + scroll;
                long endInWindow = endTick + scroll;

                boolean beforePassthrough = fromInWindow > view.height();
                boolean afterPassthrough = endInWindow < 0;
                boolean wayPassthrough = fromInWindow < 0;

                if(wayPassthrough) {
                    noteCount++;
                }

                if(beforePassthrough || afterPassthrough) {
                    continue;
                }

                boolean pressed = -scroll > fromTick && -scroll < endTick;

                Color color;
                if(pressed) {
                    color = PRESSED_COLORS[trackId % PRESSED_COLORS.length];
                } else {
                    color = COLORS[trackId % COLORS.length];
                }
                // add alpha to visualize velocity in piano roll
                int alpha = Math.min(255, (int) (note.getVelocity() / 64.0 * 255));
                color = new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
                if(pressed) {
                    note.setPressedColor(color);
                } else {
                    note.setColor(color);
                }
                graphics.setColor(color);

                int yDiff = (int) (endTick - fromTick);
                graphics.fillRect(indent + note.getKey() * NOTE_WIDTH, height - (int) (fromTick + scroll) - yDiff, NOTE_WIDTH, yDiff);

                if(pressed) {
                    pressedNotes.add(note);
                }
            }
        }

        graphics.setColor(Color.DARK_GRAY);
        graphics.drawLine(0, height, view.width(), height);
        for(Line line : reader.getLines()) {
            int y = height - (int) (line.getTick() / multiply + scroll);
            graphics.drawLine(0, y, view.width(), y);
        }

        for(Temp temp : reader.getTemps()) {
            int y = height - (int) (temp.getTick() / multiply + scroll) - 10;
            drawString(graphics, String.valueOf(temp.getBpm()), 10, y);
        }

        drawKeyboard(graphics, indent, height, reader.getLowestKey(), reader.getHighestKey());

        for(int i = 0; i < pressedNotes.size(); i++) {
            Note note = pressedNotes.get(i);

            graphics.setColor(note.getPressedColor());
            if(note.isSharp()) {
                graphics.fillRect((indent + note.getKey() * NOTE_WIDTH) + 1, height + 1, NOTE_WIDTH - 2, 37);
            } else {
                graphics.fillRect((indent + note.getKey() * NOTE_WIDTH) + 1, height + 41, NOTE_WIDTH - 2, 25);
            }
        }

        graphics.setColor(Color.WHITE);

        List<String> summary = new ArrayList<>();
        summary.add("Notes: " + noteCount);
        summary.add("Multiply: " + formattedMultiply);
        if(trackScreen.isPaused()) {
            summary.add("PAUSE");
        }
        if(view.data(END_OF_TRACK)) {
            summary.add("END OF TRACK");
        }
        drawStrings(graphics, summary, 25, getPianoRollHorizontalStartPoint(view) + 35);
    }

    private void drawKeyboard(Graphics graphics, int indent, int height, int min, int max) {
        for(int i = min; i <= max; i++) {
            int x = indent + (i + 1) * NOTE_WIDTH - NOTE_WIDTH;
            int index = (i + KEYS.length) % KEYS.length;
            if(KEYS[index] == 1) { // sharp
                graphics.setColor(Color.BLACK);
                graphics.fillRect(x, height, NOTE_WIDTH, SHARP_HEIGHT);
                if(i < PIANO_MIN || i > PIANO_MAX) { // 20 + 87
                    graphics.setColor(new Color(212, 212, 212));
                } else {
                    graphics.setColor(Color.WHITE);
                }
                graphics.fillRect(x, height + SHARP_HEIGHT, NOTE_WIDTH, NORMAL_HEIGHT);
                graphics.setColor(Color.BLACK);

                int lineX = x + (NOTE_WIDTH / 2);
                graphics.drawLine(lineX, height + SHARP_HEIGHT, lineX, height + KEYBOARD_HEIGHT);

                if(i == PIANO_MIN - 1) {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(lineX + 1, height + SHARP_HEIGHT, (NOTE_WIDTH / 2) - 1, height + KEYBOARD_HEIGHT);
                } else if(i == PIANO_MAX + 1) {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(x, height + SHARP_HEIGHT, (NOTE_WIDTH / 2) - 1, height + KEYBOARD_HEIGHT);
                }
            } else {
                if(i < PIANO_MIN || i > PIANO_MAX) { // 20 + 87
                    graphics.setColor(new Color(212, 212, 212));
                } else {
                    graphics.setColor(Color.WHITE);
                }
                graphics.fillRect(x, height, NOTE_WIDTH, KEYBOARD_HEIGHT);
                if(index == 5 || index == 0) { // Mi, Shi
                    graphics.setColor(Color.BLACK);
                    graphics.drawLine(x , height, x, height + KEYBOARD_HEIGHT);
                }
            }
        }
    }

    private void drawString(Graphics graphics, String str, int x, int y) {
        if(screenManager.getPreferenceManager().isSummaryEnabled()) {
            graphics.drawString(str, x, y);
        }
    }

    private void drawStrings(Graphics graphics, List<String> strings, int x, int y) {
        for(int i = 0; i < strings.size(); i++) {
            drawString(graphics, strings.get(i), x, y + (20 * i));
        }
    }

}
