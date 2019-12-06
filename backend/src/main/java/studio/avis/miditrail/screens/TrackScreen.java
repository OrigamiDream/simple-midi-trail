package studio.avis.miditrail.screens;

import studio.avis.miditrail.Line;
import studio.avis.miditrail.MIDITrail;
import studio.avis.miditrail.Note;
import studio.avis.miditrail.TrackReader;
import studio.avis.miditrail.configurations.Playlist;
import studio.avis.miditrail.configurations.Soundfont;
import studio.avis.miditrail.configurations.SoundfontGroup;
import studio.avis.miditrail.soundfonts.SoundfontManager;
import studio.avis.juikit.Juikit;
import studio.avis.juikit.internal.MouseListenerDelegate;

import javax.imageio.ImageIO;
import javax.sound.midi.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static studio.avis.miditrail.MIDITrail.*;

public class TrackScreen extends AbstractLoadingScreen {

    private static final String LOADING_TEXT = "Loading...";

    public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");

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

    public static final int KEYBOARD_HEIGHT = 70;
    public static final int SHARP_HEIGHT = 40;
    public static final int NORMAL_HEIGHT = KEYBOARD_HEIGHT - SHARP_HEIGHT;
    public static final int PIANO_MIN = 21;
    public static final int PIANO_MAX = 108;

    private final File file;

    private TrackReader reader;
    private KeyListener keyListener;
    private MouseListenerDelegate mouseListenerDelegate;
    private boolean running = false;

    private Synthesizer synthesizer;
    private Sequencer sequencer;

    private AtomicBoolean pause = new AtomicBoolean(true);

    private boolean withPlaylist;
    private Playlist playlist;

    public TrackScreen(Juikit juikit, ScreenManager screenManager, File file) {
        this(juikit, screenManager, file, null, false);
    }

    public TrackScreen(Juikit juikit, ScreenManager screenManager, File file, Playlist playlist, boolean withPlaylist) {
        super(juikit, screenManager);

        this.file = file;

        try {
            this.reader = new TrackReader(file);
        } catch (InvalidMidiDataException | IOException e) {
            e.printStackTrace();
        }
        screenManager.getSoundfontManager().setSoundfontListener(newSoundfont -> {
            try {
                refreshSoundfontGroup(newSoundfont);
            } catch (InvalidMidiDataException | IOException e) {
                e.printStackTrace();
            }
        });

        this.playlist = playlist;
        this.withPlaylist = withPlaylist;
    }

    @Override
    void onEnterPage() {
        running = true;
    }

    @Override
    void asyncLoad() {
        if(reader != null) {
            reader.loadTrack();
        }

        try {
            MidiSystem.getSequencer(false);
            synthesizer = MidiSystem.getSynthesizer();
            synthesizer.open();

            sequencer = MidiSystem.getSequencer();
            sequencer.open();
            sequencer.getTransmitter().setReceiver(synthesizer.getReceiver());
            sequencer.setSequence(reader.getSequence());

            refreshSoundfontGroup(screenManager.getSoundfontManager().getCurrentSoundfont());
        } catch (MidiUnavailableException | InvalidMidiDataException | IOException e) {
            e.printStackTrace();
        }

        try {
            Thread.sleep(2000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void refreshSoundfontGroup(SoundfontGroup soundfontGroup) throws InvalidMidiDataException, IOException {
        if(!soundfontGroup.isDefault()) {
            for(Instrument instrument : synthesizer.getAvailableInstruments()) {
                synthesizer.unloadInstrument(instrument);
            }
            for(Instrument instrument : synthesizer.getLoadedInstruments()) {
                synthesizer.unloadInstrument(instrument);
            }
            synthesizer.unloadAllInstruments(synthesizer.getDefaultSoundbank());

            List<Soundfont> invalid = new ArrayList<>();
            for(Soundfont soundfont : soundfontGroup.getSoundfonts()) {
                File file = new File(soundfont.getFilePath());
                if(file.exists()) {
                    synthesizer.loadAllInstruments(MidiSystem.getSoundbank(new File(soundfont.getFilePath())));
                } else {
                    invalid.add(soundfont);
                }
            }
            System.out.println("Soundfont: " + soundfontGroup.getId() + " (" + (soundfontGroup.getSoundfonts().size() - invalid.size()) + " elements)");
            if(invalid.size() > 0) {
                System.out.println("Invalid soundfonts:");
                for(Soundfont soundfont : invalid) {
                    System.out.println(" - " + soundfont.getFilePath());
                }
            }
        } else {
            System.out.println("Soundfont: " + SoundfontManager.DEFAULT_SYSTEM_SOUNDFONT_ID);
        }
    }

    public void setPause(boolean pause) {
        this.pause.set(pause);

        if(this.pause.get()) {
            sequencer.stop();
        } else {
            sequencer.start();
        }
    }

    @Override
    void onLoad() {
        juikit.data(END_OF_TRACK, false);
        juikit.data(MULTIPLY, screenManager.getPreferenceManager().getMultiply());
        juikit.data(MULTIPLY_FORMATTED, DECIMAL_FORMAT.format(screenManager.getPreferenceManager().getMultiply()));
        new Thread(() -> {
            while(running) {
                long tick = sequencer.getTickPosition();
                if(tick >= reader.getTickFinish() && !pause.get() && withPlaylist) {
                    Playlist next = screenManager.getPlaylistManager().getNextPlaylist(playlist);
                    if(next != null) {
                        screenManager.setScreen(new TrackScreen(juikit, screenManager, new File(next.getFilePath()), next, true));
                        break;
                    }
                }

                tick /= juikit.data(MULTIPLY, double.class);
                juikit.data(SCROLL, (int) -tick);
            }
        }).start();
        keyListener = new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if(e.getExtendedKeyCode() == 32 || e.getKeyCode() == 32) {
                    // SPACE
                    setPause(!pause.get());
                }
            }
        };
        juikit.keyListener(keyListener);
        mouseListenerDelegate = new MouseListenerDelegate() {
            @Override
            public void mouseClicked(MouseEvent e) {
            }

            @Override
            public void mousePressed(MouseEvent e) {
            }

            @Override
            public void mouseReleased(MouseEvent e) {
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }

            @Override
            public void mouseDragged(MouseEvent e) {
            }

            @Override
            public void mouseMoved(MouseEvent e) {
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double multiply = juikit.data(MULTIPLY);
                multiply += (-e.getWheelRotation() / 100d);
                multiply = Math.max(0.01, multiply);
                juikit.data(MULTIPLY, multiply);
                juikit.data(MULTIPLY_FORMATTED, DECIMAL_FORMAT.format(multiply));

                screenManager.getPreferenceManager().setMultiply(multiply);
            }
        };
        juikit.mouseListener(mouseListenerDelegate);
        juikit.painter((juikit, graphics) -> {
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, juikit.width(), juikit.height());

            int indent = (juikit.width() - NOTE_WIDTH * 127) / 2;
            int height = juikit.height() - juikit.data(ADDITIONAL_HEIGHT, int.class);
            int scroll = juikit.data(SCROLL);
            double multiply = juikit.data(MULTIPLY);
            String formattedMultiply = juikit.data(MULTIPLY_FORMATTED);

            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, juikit.width(), juikit.height());

            int noteCount = 0;

            List<Note> pressedNotes = new ArrayList<>();
            for(Map.Entry<Integer, List<Note>> entry : reader.getTracks().entrySet()) {
                int trackId = entry.getKey();

                List<Note> notes = entry.getValue();
                for(int i = 0; i < notes.size(); i++) {
                    Note note = notes.get(i);

                    long fromTick = note.getFromTick();
                    long endTick = note.getEndTick();

                    fromTick /= multiply;
                    endTick /= multiply;

                    long fromInWindow = fromTick + scroll;
                    long endInWindow = endTick + scroll;

                    boolean beforePassthrough = fromInWindow > juikit.height();
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
                        note.setPressedColor(color);
                    } else {
                        color = COLORS[trackId % COLORS.length];
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
            graphics.drawLine(0, height, juikit.width(), height);
            for(Line line : reader.getLines()) {
                int y = height - (int) (line.getTick() / multiply + scroll);
                graphics.drawLine(0, y, juikit.width(), y);
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
            if(pause.get()) {
                summary.add("PAUSE");
            }
            drawStrings(graphics, summary, 25, 35);
        });
        if(withPlaylist) {
            setPause(false);
        }
    }

    private void drawKeyboard(Graphics graphics, int indent, int height, int min, int max) {
        for(int i = min; i <= max; i++) {
            int x = indent + (i + 1) * NOTE_WIDTH - NOTE_WIDTH;
            int index = (i + 12) % KEYS.length;
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

    @Override
    public void leavePage() {
        juikit.painter((uikit, graphics) -> {
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, juikit.width(), juikit.height());
        });
        if(keyListener != null) {
            juikit.frame().removeKeyListener(keyListener);
        }
        if(mouseListenerDelegate != null) {
            juikit.frame().removeMouseListener(mouseListenerDelegate);
            juikit.frame().removeMouseMotionListener(mouseListenerDelegate);
            juikit.frame().removeMouseWheelListener(mouseListenerDelegate);
        }
        running = false;

        if(sequencer != null) {
            if(sequencer.isRunning()) {
                sequencer.stop();
            }
            sequencer.close();
        }
        if(synthesizer != null) {
            synthesizer.close();
        }
        screenManager.getSoundfontManager().setSoundfontListener(null);
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
