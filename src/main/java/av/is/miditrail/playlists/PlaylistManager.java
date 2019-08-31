package av.is.miditrail.playlists;

import av.is.miditrail.configurations.Configuration;
import av.is.miditrail.configurations.Playlist;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class PlaylistManager {

    public static final AtomicInteger ID_GENERATOR = new AtomicInteger();

    private final Configuration configuration;

    public PlaylistManager(Configuration configuration) {
        this.configuration = configuration;
    }

    public List<Playlist> getPlaylists() {
        return configuration.getPlaylists();
    }

    public Playlist getNextPlaylist(Playlist currentPlaylist) {
        Playlist next = null;
        int index = 0;
        for(int i = 0; i < getPlaylists().size(); i++) {
            Playlist playlist = getPlaylists().get(i);
            if(playlist.getId() == currentPlaylist.getId()) {
                index = i;
            }
        }
        if(getPlaylists().size() > index + 1) {
            next = getPlaylists().get(index + 1);
        }
        return next;
    }

    public void clearPlaylist() {
        configuration.getPlaylists().clear();
        configuration.save();
    }

    public void addPlaylist(File file) {
        if(file.isDirectory()) {
            return;
        }
        Playlist playlist = new Playlist(file.getName(), file.getAbsolutePath());
        playlist.setId(ID_GENERATOR.getAndIncrement());
        configuration.getPlaylists().add(playlist);
        configuration.save();
    }
}
