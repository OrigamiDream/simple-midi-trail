package studio.avis.miditrail;

public class Line {

    private final long tick;

    private boolean finale = false;

    public Line(long tick) {
        this.tick = tick;
    }

    public boolean isFinale() {
        return finale;
    }

    public void setFinale(boolean finale) {
        this.finale = finale;
    }

    public long getTick() {
        return tick;
    }
}
