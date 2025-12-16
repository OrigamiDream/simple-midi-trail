package studio.avis.miditrail;

import java.util.Objects;

public class KeySignature {

    public KeySignature(long tick, Gender gender, int signature) {
        this.tick = tick;
        this.gender = gender;
        this.signature = signature;
    }

    public enum Gender {
        MAJOR,
        MINOR
    }

    private final long tick;
    private final Gender gender;
    private final int signature;

    public long getTick() {
        return tick;
    }

    public Gender getGender() {
        return gender;
    }

    public int getSignature() {
        return signature;
    }

    public int getFlats() {
        int sig = signature - 7;
        sig = Math.min(sig, 0);
        return Math.abs(sig);
    }

    public int getSharps() {
        int sig = signature - 7;
        sig = Math.max(sig, 0);
        return sig;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KeySignature that = (KeySignature) o;
        return tick == that.tick;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tick);
    }
}
