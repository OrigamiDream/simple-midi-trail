package av.is.miditrail;

import java.util.Objects;

public class Note {

    private final long fromTick;
    private long endTick;
    private final int key;

    public Note(long fromTick, int key) {
        this.fromTick = fromTick;
        this.key = key;
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
