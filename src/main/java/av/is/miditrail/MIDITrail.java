package av.is.miditrail;

import av.is.miditrail.configurations.Configuration;
import av.is.miditrail.configurations.Playlist;
import av.is.miditrail.menus.MenuBarManager;
import av.is.miditrail.playlists.PlaylistManager;
import av.is.miditrail.screens.EmptyScreen;
import av.is.miditrail.screens.ScreenManager;
import av.is.miditrail.soundfonts.SoundfontManager;
import studio.avis.juikit.Juikit;

import javax.swing.*;
import java.awt.*;
import java.io.*;

public class MIDITrail {

    public static final String CONFIGURATION_FILE_NAME = "config.middata";

    public static final int NOTE_WIDTH = 10;
    public static final int DEFAULT_UI_INDENT = 50;
    public static final int DEFAULT_UI_HEIGHT = 500;

    public static final int[] KEYS = { 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 1, 0 };

    public static final String DONE = "DONE";
    public static final String LOADING = "LOADING";
    public static final String OPACITY = "OPACITY";
    public static final String RUNNING = "RUNNING";
    public static final String SCROLL = "SCROLL";
    public static final String MULTIPLY = "MULT";
    public static final String MULTIPLY_FORMATTED = "MULT_FORMATTED";
    public static final String ADDITIONAL_HEIGHT = "ADDITIONAL_HEIGHT";
    public static final String END_OF_TRACK = "EOT";

    public static void main(String[] args) throws ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException, IllegalAccessException {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        Configuration configuration;
        try(FileInputStream fileInputStream = new FileInputStream(new File(CONFIGURATION_FILE_NAME));
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {

            configuration = (Configuration) objectInputStream.readObject();
        } catch (Exception e) {
            configuration = new Configuration();
        }
        Juikit juikit = Juikit.createFrame();

        for(Playlist playlist : configuration.getPlaylists()) {
            playlist.setId(PlaylistManager.ID_GENERATOR.getAndIncrement());
        }

        if(juikit.macOS()) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
        }

        SoundfontManager soundfontManager = new SoundfontManager(configuration);
        PlaylistManager playlistManager = new PlaylistManager(configuration);

        ScreenManager screenManager = new ScreenManager(soundfontManager, playlistManager);

        new MenuBarManager(juikit, screenManager, configuration, soundfontManager, playlistManager).init();
        
        int additionalHeight;
        if(juikit.windows()) {
            additionalHeight = 130;
        } else {
            additionalHeight = 90;
        }

        juikit.title("MIDI Trail")
                .size((NOTE_WIDTH * 127) + (DEFAULT_UI_INDENT * 2), DEFAULT_UI_HEIGHT + additionalHeight)
                .centerAlign()
                .repaintInterval(10L)
                .closeOperation(WindowConstants.EXIT_ON_CLOSE)
                .background(Color.BLACK)
                .visibility(true);
        
        juikit.data(ADDITIONAL_HEIGHT, additionalHeight);
        juikit.data(SCROLL, 0);
        juikit.data(MULTIPLY, 1d);
        juikit.data(MULTIPLY_FORMATTED, "1");
        juikit.data(LOADING, 0).data(OPACITY, 0).data(DONE, true).data(RUNNING, false);

        screenManager.setScreen(new EmptyScreen(juikit, screenManager));
    }

}
