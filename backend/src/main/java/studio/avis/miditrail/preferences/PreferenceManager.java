package studio.avis.miditrail.preferences;

import studio.avis.miditrail.configurations.Configuration;

import java.util.concurrent.atomic.AtomicBoolean;

public class PreferenceManager {

    private final Configuration configuration;

    private final AtomicBoolean needRefresh = new AtomicBoolean(false);

    public PreferenceManager(Configuration configuration) {
        this.configuration = configuration;

        Thread thread = new Thread(() -> {
            while(true) {
                try {
                    Thread.sleep(2000);
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
                if(!needRefresh.get()) {
                    continue;
                }
                configuration.save();
                needRefresh.set(false);
                System.out.println("Preference refresh dequeue proceed.");
            }
        });
        thread.setName("MIDI Trail dequeue processor");
        thread.setDaemon(true);
        thread.start();;
    }

    public double getMultiply() {
        return configuration.getPreference().getMultiply();
    }

    public void setMultiply(double multiply) {
        needRefresh.set(true);
        configuration.getPreference().setMultiply(multiply);
    }

    public boolean isSummaryEnabled() {
        return configuration.getPreference().isSummaryEnabled();
    }

    public void setSummary(boolean summary) {
        needRefresh.set(true);
        configuration.getPreference().setSummary(summary);
    }
}
