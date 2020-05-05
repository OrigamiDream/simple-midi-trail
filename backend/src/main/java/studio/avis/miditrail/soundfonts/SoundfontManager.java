package studio.avis.miditrail.soundfonts;

import studio.avis.miditrail.configurations.Configuration;
import studio.avis.miditrail.configurations.Soundfont;
import studio.avis.miditrail.configurations.SoundfontGroup;

import java.io.File;
import java.util.*;

public class SoundfontManager {

    @FunctionalInterface
    public interface SoundfontListener {

        void onChange(SoundfontGroup newSoundfont);

    }

    public static final String DEFAULT_SYSTEM_SOUNDFONT_ID = "Default System Soundfont";

    private final Configuration configuration;
    private SoundfontListener soundfontListener;

    public SoundfontManager(Configuration configuration) {
        this.configuration = configuration;

        boolean defaultSoundfont = false;
        List<SoundfontGroup> soundfontGroups = getSoundfonts();
        for(SoundfontGroup soundfontGroup : soundfontGroups) {
            if(soundfontGroup.getSoundfonts().size() == 1) {
                Soundfont soundfont = soundfontGroup.getSoundfonts().get(0);
                if(!soundfont.isFile() && soundfont.getFilePath().equals("INVALID") && soundfont.getFileName().equals(DEFAULT_SYSTEM_SOUNDFONT_ID)) {
                    defaultSoundfont = true;
                    break;
                }
            }
        }

        if(!defaultSoundfont) {
            Soundfont soundfont = new Soundfont(DEFAULT_SYSTEM_SOUNDFONT_ID, "INVALID", false);
            SoundfontGroup soundfontGroup = new SoundfontGroup(DEFAULT_SYSTEM_SOUNDFONT_ID, Collections.singletonList(soundfont));
            configuration.getSoundfontGroups().add(soundfontGroup);
            setCurrentSoundfont(soundfontGroup);
        }
    }

    public List<SoundfontGroup> getSoundfonts() {
        return configuration.getSoundfontGroups();
    }

    public void addSoundfontFile(File file) {
        if(file.isDirectory()) {
            return;
        }
        Soundfont soundfont = new Soundfont(file.getName(), file.getAbsolutePath(), true);
        SoundfontGroup soundfontGroup = new SoundfontGroup(file.getName(), new ArrayList<>(Collections.singletonList(soundfont)));
        configuration.getSoundfontGroups().add(soundfontGroup);
        setCurrentSoundfont(soundfontGroup);
    }

    public void addSoundfontDirectory(File directory) {
        if(!directory.isDirectory()) {
            return;
        }
        List<Soundfont> soundfonts = new ArrayList<>();
        List<File> soundfontFiles = findSoundfontFile(directory, new ArrayList<>());
        for(File file : soundfontFiles) {
            soundfonts.add(new Soundfont(file.getName(), file.getAbsolutePath(), true));
        }
        SoundfontGroup soundfontGroup = new SoundfontGroup(directory.getName(), soundfonts);
        configuration.getSoundfontGroups().add(soundfontGroup);
        setCurrentSoundfont(soundfontGroup);
    }

    public void setCurrentSoundfont(SoundfontGroup soundfont) {
        if(soundfontListener != null) {
            soundfontListener.onChange(soundfont);
        }

        configuration.setLastSoundfont(soundfont);
        configuration.save();
    }

    public SoundfontGroup getCurrentSoundfont() {
        return configuration.getLastSoundfont();
    }

    private List<File> findSoundfontFile(File directory, List<File> files) {
        for(File file : Objects.requireNonNull(directory.listFiles())) {
            String name = file.getName();
            if(name.toLowerCase().endsWith(".sf2") || name.toLowerCase().endsWith(".sf3")) {
                files.add(file);
                continue;
            }

            if(file.isDirectory()) {
                findSoundfontFile(file, files);
            }
        }
        return files;
    }

    public void setSoundfontListener(SoundfontListener soundfontListener) {
        if(soundfontListener == null) {
            System.out.println("Unregistering soundfont delegate...");
        } else {
            System.out.println("Registering new soundfont delegate...");
        }
        this.soundfontListener = soundfontListener;
    }
}
