package studio.avis.miditrail.screens;

import studio.avis.miditrail.playlists.PlaylistManager;
import studio.avis.miditrail.preferences.PreferenceManager;
import studio.avis.miditrail.soundfonts.SoundfontManager;

public class ScreenManager {

    private final SoundfontManager soundfontManager;
    private final PlaylistManager playlistManager;
    private final PreferenceManager preferenceManager;
    private AbstractScreen screen;

    public ScreenManager(SoundfontManager soundfontManager, PlaylistManager playlistManager, PreferenceManager preferenceManager) {
        this.soundfontManager = soundfontManager;
        this.playlistManager = playlistManager;
        this.preferenceManager = preferenceManager;
    }

    public SoundfontManager getSoundfontManager() {
        return soundfontManager;
    }

    public PlaylistManager getPlaylistManager() {
        return playlistManager;
    }

    public PreferenceManager getPreferenceManager() {
        return preferenceManager;
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
