package av.is.miditrail.screens;

import av.is.miditrail.*;
import avis.juikit.Juikit;

import javax.imageio.ImageIO;
import javax.sound.midi.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static av.is.miditrail.MIDITrail.NOTE_WIDTH;

public class TrackScreen extends AbstractLoadingScreen {

    private static final String LOADING_TEXT = "Loading...";

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

    private final File file;

    private TrackReader reader;
    private KeyListener keyListener;
    private boolean running = false;

    private Synthesizer synthesizer;
    private Sequencer sequencer;

    private AtomicReference<Image> pianoKeyboard = new AtomicReference<>();
    private AtomicBoolean pause = new AtomicBoolean(true);

    public TrackScreen(Juikit juikit, ScreenManager screenManager, File file) {
        super(juikit, screenManager);

        this.file = file;

        try {
            this.reader = new TrackReader(file);
        } catch (InvalidMidiDataException | IOException e) {
            e.printStackTrace();
        }
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
        Image image = null;
        try {
            image = ImageIO.read(MIDITrail.class.getResourceAsStream("/image/keyboard.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        pianoKeyboard.set(image);

        try {
            MidiSystem.getSequencer(false);
            synthesizer = MidiSystem.getSynthesizer();
            synthesizer.open();

            Soundfonts.Soundfont soundfont = Soundfonts.CURRENT_SOUNDFONT.get();
            if(!soundfont.path.equals("INVALID")) {
                synthesizer.unloadAllInstruments(synthesizer.getDefaultSoundbank());
                synthesizer.loadAllInstruments(MidiSystem.getSoundbank(new BufferedInputStream(MIDITrail.class.getResourceAsStream(Soundfonts.CURRENT_SOUNDFONT.get().path))));
            }

            sequencer = MidiSystem.getSequencer();
            sequencer.open();
            sequencer.getTransmitter().setReceiver(synthesizer.getReceiver());
            sequencer.setSequence(reader.getSequence());
        } catch (MidiUnavailableException | InvalidMidiDataException | IOException e) {
            e.printStackTrace();
        }

        try {
            Thread.sleep(2000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    void onLoad() {
        new Thread(() -> {
            while(running) {
                long tick = sequencer.getTickPosition();
                juikit.data("SCROLL", (int) -tick);
            }
        }).start();
        keyListener = new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                switch (e.getExtendedKeyCode()) {
                    case 32: // SPACE
                        pause.set(!pause.get());

                        if(pause.get()) {
                            sequencer.stop();
                        } else {
                            sequencer.start();
                        }
                        break;
                }
            }

            @Override
            public void keyPressed(KeyEvent e) {
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }
        };
        juikit.keyListener(keyListener);
        juikit.painter((juikit, graphics) -> {
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, juikit.width(), juikit.height());

            int indent = (juikit.width() - NOTE_WIDTH * 127) / 2;
            int height = juikit.height() - 90;
            int scroll = juikit.data("SCROLL");

            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, juikit.width(), juikit.height());

            int noteCount = 0;

            java.util.List<Note> pressedNotes = new ArrayList<>();
            for(Map.Entry<Integer, java.util.List<Note>> entry : reader.getTracks().entrySet()) {
                int trackId = entry.getKey();

                List<Note> notes = entry.getValue();
                for(int i = 0; i < notes.size(); i++) {
                    Note note = notes.get(i);

                    long fromTick = note.getFromTick();
                    long endTick = note.getEndTick();

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

                    int yDiff = (int) (note.getEndTick() - note.getFromTick());
                    graphics.fillRect(indent + note.getKey() * NOTE_WIDTH, height - (int) (note.getFromTick() + scroll) - yDiff, NOTE_WIDTH, yDiff);

                    if(pressed) {
                        pressedNotes.add(note);
                    }
                }
            }

            graphics.setColor(Color.DARK_GRAY);
            graphics.drawLine(0, height, juikit.width(), height);
            for(Line line : reader.getLines()) {
                int y = height - (int) (line.getTick() + scroll);
                graphics.drawLine(0, y, juikit.width(), y);
            }

            graphics.drawImage(pianoKeyboard.get(), indent + (NOTE_WIDTH * 21), height, (NOTE_WIDTH * 88), 70, juikit.panel());

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
            graphics.drawString("Notes: " + noteCount, 25, 35);
            if(pause.get()) {
                graphics.drawString("PAUSE", 25, 55);
            }
        });
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
    }
}
