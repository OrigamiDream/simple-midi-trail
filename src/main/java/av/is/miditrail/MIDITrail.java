package av.is.miditrail;

import av.is.miditrail.screens.EmptyScreen;
import av.is.miditrail.screens.ScreenManager;
import av.is.miditrail.screens.TrackScreen;
import avis.juikit.Juikit;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;

public class MIDITrail {

    public static final int NOTE_WIDTH = 10;
    public static final int DEFAULT_UI_INDENT = 50;
    public static final int DEFAULT_UI_HEIGHT = 500;

    public static final int[] KEYS = { 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 1, 0 };

    public static final String DONE = "DONE";
    public static final String LOADING = "LOADING";
    public static final String OPACITY = "OPACITY";
    public static final String RUNNING = "RUNNING";

    public static void main(String[] args) throws ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException, IllegalAccessException {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        Juikit juikit = Juikit.createFrame();

        if(juikit.macOS()) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
        }

        ScreenManager screenManager = new ScreenManager();

        setupMenuBar(juikit, screenManager);

        juikit.title("MIDI Trail")
                .size((NOTE_WIDTH * 127) + (DEFAULT_UI_INDENT * 2), DEFAULT_UI_HEIGHT + 90)
                .centerAlign()
                .repaintInterval(10L)
                .closeOperation(WindowConstants.EXIT_ON_CLOSE)
                .background(Color.BLACK)
                .visibility(true);

        juikit.data(LOADING, 0).data(OPACITY, 0).data(DONE, true).data(RUNNING, false);

        screenManager.setScreen(new EmptyScreen(juikit, screenManager));
    }

    private static void setupMenuBar(Juikit juikit, ScreenManager screenManager) {
        JMenuBar fileBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        JMenuItem open = new JMenuItem(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FileDialog dialog = new FileDialog(juikit.frame());
                dialog.setFilenameFilter((dir, name) -> name.endsWith(".mid") || name.endsWith(".midi"));
                dialog.setVisible(true);

                if(dialog.getFile() != null) {
                    screenManager.setScreen(new TrackScreen(juikit, screenManager, new File(dialog.getDirectory() + dialog.getFile())));
                }
            }
        });
        open.setAccelerator(KeyStroke.getKeyStroke('O', Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        open.setText("Open...");

        fileMenu.add(open);

        fileBar.add(fileMenu);

        juikit.frame().setJMenuBar(fileBar);
        juikit.frame().pack();
    }

}
