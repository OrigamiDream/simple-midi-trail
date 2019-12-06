package studio.avis.miditrail.configurations;

import java.io.Serializable;

public class Preference implements Serializable {

    private double multiply = 1d;
    private boolean summary = true;

    public void setMultiply(double multiply) {
        this.multiply = multiply;
    }

    public double getMultiply() {
        return multiply;
    }

    public void setSummary(boolean summary) {
        this.summary = summary;
    }

    public boolean isSummaryEnabled() {
        return summary;
    }
}
