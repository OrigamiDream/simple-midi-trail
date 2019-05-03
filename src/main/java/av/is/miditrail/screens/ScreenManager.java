package av.is.miditrail.screens;

import av.is.miditrail.soundfonts.SoundfontManager;

public class ScreenManager {

    private final SoundfontManager soundfontManager;
    private AbstractScreen screen;

    public ScreenManager(SoundfontManager soundfontManager) {
        this.soundfontManager = soundfontManager;
    }

    public SoundfontManager getSoundfontManager() {
        return soundfontManager;
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
