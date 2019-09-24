package studio.avis.miditrail.configurations;

import studio.avis.miditrail.soundfonts.SoundfontManager;

import javax.swing.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SoundfontGroup implements Serializable {

    private static final long serialVersionUID = -8790664581010444129L;

    private String id;
    private List<Soundfont> soundfonts;

    private transient JMenuItem menuItem;

    public SoundfontGroup(String id, List<Soundfont> soundfonts) {
        this.id = id;
        this.soundfonts = soundfonts;
    }

    public List<Soundfont> getSoundfonts() {
        if(soundfonts == null) {
            soundfonts = new ArrayList<>();
        }
        return soundfonts;
    }

    public String getId() {
        return id;
    }

    public JMenuItem getMenuItem() {
        return menuItem;
    }

    public void setMenuItem(JMenuItem menuItem) {
        this.menuItem = menuItem;
    }

    public boolean isDefault() {
        return id.equals(SoundfontManager.DEFAULT_SYSTEM_SOUNDFONT_ID);
    }
}
