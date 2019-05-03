package av.is.miditrail.configurations;

import java.io.Serializable;

public class Soundfont implements Serializable {

    private static final long serialVersionUID = -6832957766675036548L;

    private final String fileName;
    private final String filePath;

    private final boolean file;

    public Soundfont(String fileName, String filePath, boolean file) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.file = file;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public boolean isFile() {
        return file;
    }
}
