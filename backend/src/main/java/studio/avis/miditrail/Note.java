package studio.avis.miditrail;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static studio.avis.miditrail.MIDITrail.KEYS;

public class Note {

    private final long fromTick;
    private long endTick;
    private final int key;
    private final int velocity;

    private final boolean sharp;
    private Color color;
    private Color pressedColor;

    private int tieTo;

    public Note(long fromTick, int key, int velocity) {
        this.fromTick = fromTick;
        this.key = key;
        this.velocity = velocity;

        this.sharp = KEYS[key % 12] == 1;
    }

    public int getTieTo() {
        return tieTo;
    }

    public void setTieTo(int tieTo) {
        this.tieTo = tieTo;
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

    public int getVelocity() {
        return velocity;
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

    public Note copy() {
        Note note = new Note(fromTick, key, velocity);
        note.endTick = endTick;
        return note;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Note note = (Note) o;
        return fromTick == note.fromTick &&
                endTick == note.endTick &&
                key == note.key;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromTick, endTick, key);
    }
}
