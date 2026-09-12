package studio.avis.miditrail;

public class Temp {

    private final long tick;
    private final int bpm;

    public Temp(long tick, int bpm) {
        this.tick = tick;
        this.bpm = bpm;
    }

    public int getBpm() {
        return bpm;
    }

    public long getTick() {
        return tick;
    }
}
