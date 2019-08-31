package av.is.miditrail.configurations;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static av.is.miditrail.MIDITrail.CONFIGURATION_FILE_NAME;

public class Configuration implements Serializable {

    private static final long serialVersionUID = 539993014345671716L;

    private List<SoundfontGroup> soundfontGroups = new ArrayList<>();
    private List<Playlist> playlists = new ArrayList<>();

    private SoundfontGroup lastSoundfont;

    public List<SoundfontGroup> getSoundfontGroups() {
        if(soundfontGroups == null) {
            soundfontGroups = new ArrayList<>();
        }
        return soundfontGroups;
    }

    public SoundfontGroup getLastSoundfont() {
        return lastSoundfont;
    }

    public void setLastSoundfont(SoundfontGroup lastSoundfont) {
        this.lastSoundfont = lastSoundfont;
    }

    public List<Playlist> getPlaylists() {
        if(playlists == null) {
            playlists = new ArrayList<>();
        }
        return playlists;
    }

    public void save() {
        try(FileOutputStream fileOutputStream = new FileOutputStream(new File(CONFIGURATION_FILE_NAME));
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {

            objectOutputStream.writeObject(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
