package studio.avis.miditrail.screens;

import studio.avis.juikit.Juikit;
import studio.avis.juikit.internal.MouseListenerDelegate;
import studio.avis.miditrail.TrackReader;
import studio.avis.miditrail.configurations.Playlist;
import studio.avis.miditrail.configurations.Soundfont;
import studio.avis.miditrail.configurations.SoundfontGroup;
import studio.avis.miditrail.screens.views.TrackPianoRollView;
import studio.avis.miditrail.screens.views.TrackSheetView;
import studio.avis.miditrail.screens.views.TrackView;
import studio.avis.miditrail.soundfonts.SoundfontManager;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static studio.avis.miditrail.MIDITrail.*;

public class TrackScreen extends AbstractLoadingScreen {

    private static final String LOADING_TEXT = "Loading...";
    public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");

    private final File file;

    private TrackReader reader;
    private KeyListener keyListener;
    private MouseListenerDelegate mouseListenerDelegate;
    private boolean running = false;

    private Synthesizer synthesizer;
    private Sequencer sequencer;

    private final AtomicBoolean pause = new AtomicBoolean(true);
    private final AtomicInteger movementQueue = new AtomicInteger(0);

    private final boolean withPlaylist;
    private final Playlist playlist;

    private final TrackView pianoRollView;
    private final TrackView sheetView;

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

        sheetView = new TrackSheetView(reader);
        pianoRollView = new TrackPianoRollView(reader, this, screenManager);

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

        loadMidiSystem();
    }

    private void loadMidiSystem() {
        try {
            MidiSystem.getSequencer(false);
            synthesizer = MidiSystem.getSynthesizer();
            synthesizer.open();

            sequencer = MidiSystem.getSequencer();
            sequencer.open();
            if(!sequencer.isOpen()) {
                System.out.println("Failed to open sequencer... Reattempt to open...");
                sequencer.open();
            }
            sequencer.getTransmitter().setReceiver(synthesizer.getReceiver());
//            for(Track track : reader.getSequence().getTracks()) {
//                for(int i = 0; i < track.size(); i++) {
//                    MidiEvent event = track.get(i);
//                    if(event.getMessage() instanceof ShortMessage) {
//                        ShortMessage shortMessage = (ShortMessage) event.getMessage();
//                        if(shortMessage.getCommand() == ShortMessage.NOTE_ON) {
//                            if(!reader.findNoteEquivalent(shortMessage.getChannel(), event.getTick(), shortMessage.getData1())) {
//                                try {
//                                    shortMessage.setMessage(shortMessage.getCommand(), shortMessage.getChannel(), shortMessage.getData1(), 0);
//                                } catch (InvalidMidiDataException e) {
//                                    e.printStackTrace();
//                                }
//                            }
//                        }
//                    }
//                }
//            }
            sequencer.setSequence(reader.getSequence());
//            sequencer.setTempoFactor(2.0f);

            refreshSoundfontGroup(screenManager.getSoundfontManager().getCurrentSoundfont());
        } catch (MidiUnavailableException | InvalidMidiDataException | IOException e) {
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
        if(!running) {
            return;
        }
        this.pause.set(pause);

        if(this.pause.get()) {
            sequencer.stop();
        } else {
            sequencer.start();
        }
    }

    public boolean isPaused() {
        return pause.get();
    }

    private boolean hasFinished() {
        return juikit.data(END_OF_TRACK);
    }

    @Override
    void onLoad() {
        juikit.data(END_OF_TRACK, false);
        juikit.data(MULTIPLY, screenManager.getPreferenceManager().getMultiply());
        juikit.data(MULTIPLY_FORMATTED, DECIMAL_FORMAT.format(screenManager.getPreferenceManager().getMultiply()));
        new Thread(() -> {
            while(running) {
                long tick = sequencer.getTickPosition();
                juikit.data(END_OF_TRACK, tick >= sequencer.getTickLength());
                if(tick >= sequencer.getTickLength() && !pause.get() && withPlaylist) {
                    Playlist next = screenManager.getPlaylistManager().getNextPlaylist(playlist);
                    if(next != null) {
                        screenManager.setScreen(new TrackScreen(juikit, screenManager, new File(next.getFilePath()), next, true));
                        break;
                    }
                }

                tick /= juikit.data(MULTIPLY, double.class);
                juikit.data(TICK, sequencer.getTickPosition());
                juikit.data(SCROLL, (int) -tick);
            }
        }).start();
        new Thread(() -> {
            while(running) {
                try {
                    Thread.sleep(5L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                int ticks;
                if(movementQueue.get() > 0) {
                    ticks = movementQueue.decrementAndGet();
                } else if(movementQueue.get() < 0) {
                    ticks = movementQueue.incrementAndGet();
                } else {
                    continue;
                }
                moveActual(ticks);
            }
        }).start();
        keyListener = new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if(e.getExtendedKeyCode() == 37 || e.getKeyCode() == 37) {
                    // LEFT ARROW
                    movePosition(-50, false, false);
                } else if(e.getExtendedKeyCode() == 39 || e.getKeyCode() == 39) {
                    // RIGHT ARROW
                    movePosition(50, false, false);
                }
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
        juikit.panel().requestFocus();
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
                if(e.isShiftDown()) {
                    movePosition((long) (-e.getWheelRotation() / multiply * 50), true, true);
                } else {
                    multiply += (-e.getWheelRotation() / 100d);
                    multiply = Math.max(0.01, multiply);
                    juikit.data(MULTIPLY, multiply);
                    juikit.data(MULTIPLY_FORMATTED, DECIMAL_FORMAT.format(multiply));

                    screenManager.getPreferenceManager().setMultiply(multiply);
                }
            }
        };
        juikit.mouseListener(mouseListenerDelegate);
        juikit.painter((juikitView, graphics) -> {
            pianoRollView.draw(juikitView, graphics);
            sheetView.draw(juikitView, graphics);
        });

        if(withPlaylist) {
            setPause(false);
        }
    }

    private void movePosition(long ticks, boolean force, boolean scroll) {
        if(!scroll) {
            if(movementQueue.get() != 0 && !force) {
                return;
            }
            movementQueue.set(movementQueue.get() + (int) ticks);
        } else {
            moveActual(ticks);
        }
    }

    private void moveActual(long ticks) {
        if(!running) {
            return;
        }
        long maxPosition = sequencer.getTickLength();
        long currentPosition = sequencer.getTickPosition();

        currentPosition = Math.max(0, Math.min(maxPosition, currentPosition + ticks));
        if(currentPosition == 0 || currentPosition == maxPosition) {
            movementQueue.set(0);
        }
        sequencer.setTickPosition(currentPosition);

        currentPosition /= juikit.data(MULTIPLY, double.class);
        juikit.data(TICK, currentPosition);
        juikit.data(SCROLL, (int) -currentPosition);
    }

    @Override
    public void leavePage() {
        juikit.painter((juikitView, graphics) -> {
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, juikitView.width(), juikitView.height());
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
            sequencer = null;
        }
        if(synthesizer != null) {
            synthesizer.close();
            synthesizer = null;
        }
    }
}
