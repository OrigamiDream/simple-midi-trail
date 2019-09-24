package studio.avis.miditrail.configurations;

import java.io.Serializable;
import java.util.Objects;

public class Playlist implements Serializable {

    private static final long serialVersionUID = 3272262466376791241L;

    private final String fileName;
    private final String filePath;

    private transient int id;

    public Playlist(String fileName, String filePath) {
        this.fileName = fileName;
        this.filePath = filePath;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;
        Playlist playlist = (Playlist) o;
        return id == playlist.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
