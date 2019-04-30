package av.is.miditrail.screens;

public class ScreenManager {

    private AbstractScreen screen;

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
