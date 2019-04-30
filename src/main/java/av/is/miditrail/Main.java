package av.is.miditrail;

import avis.juikit.Juikit;

import javax.imageio.ImageIO;
import javax.sound.midi.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class Main {

    // Configuration start
    private static final String SOUNDFONT_PATH = "src/main/resources/soundfonts/Full Grand Piano.sf2";
    private static final String MIDI_PATH = "src/main/resources/midi/Evans.mid";
    private static final String PIANO_PATH = "src/main/resources/image/keyboard.png";
    private static final int NOTE_WIDTH = 10;
    private static final int UI_INDENT = 50;
    private static final boolean REVERSE = true;
    private static final int UI_HEIGHT = 500;
    // Configuration end

    private static final Map<Integer, List<Note>> TRACKS = new HashMap<>();
    private static final List<Line> LINES = new ArrayList<>();

    static final int[] KEYS = { 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 1, 0 };

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

    public static void main(String[] args) throws InvalidMidiDataException, IOException, MidiUnavailableException, InterruptedException {
        File file = new File(MIDI_PATH);

        Image image = ImageIO.read(new File(PIANO_PATH));

        Sequence sequence = MidiSystem.getSequence(file);

        AtomicInteger atomicTrackId = new AtomicInteger();
        AtomicLong atomicTick = new AtomicLong();

        Stream.of(sequence.getTracks()).forEach(track -> {
            int trackId = atomicTrackId.getAndIncrement();

            TRACKS.put(trackId, new ArrayList<>());

            List<Note> notes = TRACKS.get(trackId);
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
                }

                long prevTick = atomicTick.get();
                if(tick > prevTick) {
                    atomicTick.set(tick);
                }
            }

            System.out.println(notes.size() + " Notes of Track #" + trackId);
        });

        for(long i = 0; i < atomicTick.get(); i += 1000) {
            LINES.add(new Line(i));
        }

        Juikit uikit = Juikit.createFrame()
                .title("MIDI Trail")
                .size((NOTE_WIDTH * 127) + (UI_INDENT * 2), UI_HEIGHT + 90)
                .centerAlign()
                .repaintInterval(1L)

                .data("SCROLL", 0)

                .painter((juikit, graphics) -> {
                    graphics.setColor(Color.BLACK);
                    graphics.fillRect(0, 0, juikit.width(), juikit.height());

                    int scroll = juikit.data("SCROLL");

                    List<Note> pressedNotes = new ArrayList<>();
                    for(Map.Entry<Integer, List<Note>> entry : TRACKS.entrySet()) {
                        int trackId = entry.getKey();

                        List<Note> notes = entry.getValue();
                        for(int i = 0; i < notes.size(); i++) {
                            Note note = notes.get(i);

                            long fromTick = note.getFromTick();
                            long endTick = note.getEndTick();

                            long fromInWindow = fromTick + scroll;
                            long endInWindow = endTick + scroll;

                            if(fromInWindow > juikit.height() || endInWindow < 0) {
                                continue;
                            }

                            boolean pressed = -scroll >= fromTick && -scroll < endTick;

                            Color color;
                            if(pressed) {
                                color = PRESSED_COLORS[trackId % PRESSED_COLORS.length];
                                note.setPressedColor(color);
                            } else {
                                color = COLORS[trackId % COLORS.length];
                                note.setColor(color);
                            }

                            graphics.setColor(color);

                            if(REVERSE) {
                                int yDiff = (int) (note.getEndTick() - note.getFromTick());
                                graphics.fillRect(UI_INDENT + note.getKey() * NOTE_WIDTH, UI_HEIGHT - (int) (note.getFromTick() + scroll) - yDiff, NOTE_WIDTH, yDiff);
                            } else {
                                graphics.fillRect(UI_INDENT + note.getKey() * NOTE_WIDTH, (int) note.getFromTick() + scroll, -NOTE_WIDTH, (int) (note.getEndTick() - note.getFromTick()));
                            }

                            if(pressed) {
                                pressedNotes.add(note);
                            }
                        }
                    }

                    graphics.setColor(Color.DARK_GRAY);
                    if(REVERSE) {
                        graphics.drawLine(0, UI_HEIGHT, juikit.width(), UI_HEIGHT);
                    }
                    for(Line line : LINES) {
                        if(REVERSE) {
                            int y = UI_HEIGHT - (int) (line.getTick() + scroll);
                            graphics.drawLine(0, y, juikit.width(), y);
                        } else {
                            int y = (int) (line.getTick() + scroll);
                            graphics.drawLine(0, y, juikit.width(), y);
                        }
                    }

                    if(REVERSE) {
                        graphics.drawImage(image, 50 + (NOTE_WIDTH * 21), UI_HEIGHT, (NOTE_WIDTH * 88), 70, juikit.panel());
                    }

                    for(int i = 0; i < pressedNotes.size(); i++) {
                        Note note = pressedNotes.get(i);

                        graphics.setColor(note.getPressedColor());
                        if(note.isSharp()) {
                            graphics.fillRect((UI_INDENT + note.getKey() * NOTE_WIDTH) + 1, UI_HEIGHT + 1, NOTE_WIDTH - 2, 37);
                        } else {
                            graphics.fillRect((UI_INDENT + note.getKey() * NOTE_WIDTH) + 1, UI_HEIGHT + 41, NOTE_WIDTH - 2, 25);
                        }
                    }
                })

                .mouseWheelMoved((juikit, mouseEvent) -> {
                    MouseWheelEvent event = (MouseWheelEvent) mouseEvent;

                    juikit.data("SCROLL", juikit.data("SCROLL", Integer.class) + event.getWheelRotation() * -2);
                })

                .closeOperation(WindowConstants.EXIT_ON_CLOSE)
                .background(Color.BLACK)
                .resizable(false)
                .visibility(true);

        MidiSystem.getSequencer(false);

        Synthesizer synthesizer = MidiSystem.getSynthesizer();
        synthesizer.open();
        synthesizer.unloadAllInstruments(synthesizer.getDefaultSoundbank());
        synthesizer.loadAllInstruments(MidiSystem.getSoundbank(new File(SOUNDFONT_PATH)));

        Sequencer sequencer = MidiSystem.getSequencer();
        sequencer.open();
        sequencer.getTransmitter().setReceiver(synthesizer.getReceiver());
        sequencer.setSequence(sequence);

        Thread.sleep(2000L);

        sequencer.start();

        while(true) {
            long tick = sequencer.getTickPosition();
            uikit.data("SCROLL", (int) -tick);
        }
    }

}
