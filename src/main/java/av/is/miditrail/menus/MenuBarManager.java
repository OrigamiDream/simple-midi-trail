package av.is.miditrail.menus;

import av.is.miditrail.Configuration;
import av.is.miditrail.menus.file.MenuFile;
import av.is.miditrail.menus.soundfont.MenuSoundfont;
import av.is.miditrail.screens.ScreenManager;
import avis.juikit.Juikit;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class MenuBarManager {

    private final Juikit juikit;
    private final ScreenManager screenManager;
    private final Configuration configuration;

    private final List<Menu> menus = new ArrayList<>();

    public MenuBarManager(Juikit juikit, ScreenManager screenManager, Configuration configuration) {
        this.juikit = juikit;
        this.screenManager = screenManager;
        this.configuration = configuration;

        this.menus.add(new MenuFile(this));
        this.menus.add(new MenuSoundfont(this));
    }

    public Juikit getJuikit() {
        return juikit;
    }

    public ScreenManager getScreenManager() {
        return screenManager;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    private void createMenu() {
        JMenuBar menuBar = new JMenuBar();
        for(Menu menu : menus) {
            menuBar.add(menu.createMenu());
        }
        juikit.frame().setJMenuBar(menuBar);
    }

    public void refresh() {
        createMenu();
        juikit.frame().revalidate();
    }

    public void init() {
        createMenu();
        juikit.frame().pack();
    }
}
