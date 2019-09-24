package studio.avis.miditrail;

import java.awt.*;
import java.util.Objects;

import static studio.avis.miditrail.MIDITrail.KEYS;

public class Note {

    private final long fromTick;
    private long endTick;
    private final int key;

    private final boolean sharp;
    private Color color;
    private Color pressedColor;

    public Note(long fromTick, int key) {
        this.fromTick = fromTick;
        this.key = key;

        this.sharp = KEYS[key % 12] == 1;
    }

    public long getFromTick() {
        return fromTick;
    }

    public long getEndTick() {
        return endTick;
    }

    public int getKey() {
        return key;
    }

    public void setEndTick(long endTick) {
        this.endTick = endTick;
    }

    public boolean isSharp() {
        return sharp;
    }

    public Color getPressedColor() {
        return pressedColor;
    }

    public void setPressedColor(Color pressedColor) {
        this.pressedColor = pressedColor;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Note note = (Note) o;
        return key == note.key;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
}
