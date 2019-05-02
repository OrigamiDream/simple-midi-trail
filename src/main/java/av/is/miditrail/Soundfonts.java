package av.is.miditrail;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class Soundfonts {

    public static class Soundfont {

        public final String name;
        public final String path;

        public final boolean file;

        public JMenuItem menuItem;

        public Soundfont(String name, String path, boolean file) {
            this.name = name;
            this.path = path;
            this.file = file;
        }
    }

    private static final List<Soundfont> SOUNDFONTS = new ArrayList<>();
    static {
        SOUNDFONTS.add(new Soundfont("Default System Soundfont", "INVALID", false));
        SOUNDFONTS.add(new Soundfont("Z-Doc Grand Piano", "/soundfonts/Z-Doc Grand Piano.sf2", false));
        SOUNDFONTS.add(new Soundfont("Fazioli Grand Piano", "/soundfonts/Fazioli Grand Piano.sf2", false));
    }

    public static final AtomicReference<Soundfont> CURRENT_SOUNDFONT = new AtomicReference<>(SOUNDFONTS.get(0));

    public static List<Soundfont> getSoundfonts() {
        return SOUNDFONTS;
    }

    public static void loadSoundfonts(Configuration configuration) {
        for(Configuration.SoundfontFile file : configuration.getSoundfonts()) {
            if(file.fileName == null || file.filePath == null) {
                continue;
            }
            SOUNDFONTS.add(new Soundfont(file.fileName, file.filePath, true));
        }
    }

    public static void addSoundfont(Configuration configuration, File file) {
        for(Soundfont soundfont : SOUNDFONTS) {
            if(soundfont.file && soundfont.path.equals(file.getAbsolutePath()) && soundfont.name.equals(file.getName())) {
                return;
            }
        }
        System.out.println("Adding a new soundfont: " + file.getAbsolutePath());
        SOUNDFONTS.add(new Soundfont(file.getName(), file.getAbsolutePath(), true));
        configuration.getSoundfonts().add(new Configuration.SoundfontFile(file.getAbsolutePath(), file.getName()));
    }
}
