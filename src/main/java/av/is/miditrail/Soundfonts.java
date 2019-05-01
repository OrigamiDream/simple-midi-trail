package av.is.miditrail;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class Soundfonts {

    public static class Soundfont {

        public final String name;
        public final String path;

        public JCheckBoxMenuItem menuItem;

        public Soundfont(String name, String path) {
            this.name = name;
            this.path = path;
        }
    }

    private static final List<Soundfont> SOUNDFONTS = new ArrayList<>();
    static {
        SOUNDFONTS.add(new Soundfont("Default System Soundfont", "INVALID"));
        SOUNDFONTS.add(new Soundfont("Z-Doc Grand Piano", "/soundfonts/Z-Doc Grand Piano.sf2"));
        SOUNDFONTS.add(new Soundfont("Fazioli Grand Piano", "/soundfonts/Fazioli Grand Piano.sf2"));
    }

    public static final AtomicReference<Soundfont> CURRENT_SOUNDFONT = new AtomicReference<>(SOUNDFONTS.get(0));

    public static List<Soundfont> getSoundfonts() {
        return SOUNDFONTS;
    }
}
