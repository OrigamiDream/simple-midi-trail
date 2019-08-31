package av.is.miditrail.screens;

import av.is.miditrail.playlists.PlaylistManager;
import av.is.miditrail.soundfonts.SoundfontManager;

public class ScreenManager {

    private final SoundfontManager soundfontManager;
    private final PlaylistManager playlistManager;
    private AbstractScreen screen;

    public ScreenManager(SoundfontManager soundfontManager, PlaylistManager playlistManager) {
        this.soundfontManager = soundfontManager;
        this.playlistManager = playlistManager;
    }

    public SoundfontManager getSoundfontManager() {
        return soundfontManager;
    }

    public PlaylistManager getPlaylistManager() {
        return playlistManager;
    }

    public void setScreen(AbstractScreen newScreen) {
        if(screen != null) {
            screen.leavePage();
        }

        screen = newScreen;

        if(screen != null) {
            screen.enterPage();
        } else {
            System.exit(0);
        }
    }

}
