package studio.avis.miditrail;

public class TimeSignature {

    private final long tick;

    private final int numerator;
    private final int denominator;

    public TimeSignature(long tick, int numerator, int denominator) {
        this.tick = tick;
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public long getTick() {
        return tick;
    }

    public int getNumerator() {
        return numerator;
    }

    public int getDenominator() {
        return denominator;
    }
}
