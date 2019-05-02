package av.is.miditrail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static av.is.miditrail.MIDITrail.CONFIGURATION_FILE_NAME;

public class Configuration implements Serializable {

    public static class SoundfontFile implements Serializable {

        String filePath;
        String fileName;

        public SoundfontFile(String filePath, String fileName) {
            this.filePath = filePath;
            this.fileName = fileName;
        }
    }

    private List<SoundfontFile> soundfonts = new ArrayList<>();

    public List<SoundfontFile> getSoundfonts() {
        if(soundfonts == null) {
            soundfonts = new ArrayList<>();
        }
        return soundfonts;
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
