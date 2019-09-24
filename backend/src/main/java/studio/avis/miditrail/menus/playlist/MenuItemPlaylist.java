package studio.avis.miditrail.menus.playlist;

import studio.avis.miditrail.configurations.Playlist;
import studio.avis.miditrail.menus.MenuBarManager;
import studio.avis.miditrail.menus.MenuItem;
import studio.avis.miditrail.screens.TrackScreen;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;

public class MenuItemPlaylist extends MenuItem<MenuPlaylist> {

    private final Playlist playlist;

    MenuItemPlaylist(MenuPlaylist menu, MenuBarManager manager, Playlist playlist) {
        super(menu, manager);

        this.playlist = playlist;
    }

    @Override
    public JMenuItem createItem() {
        JMenuItem item = new JMenuItem(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                manager.getScreenManager().setScreen(new TrackScreen(manager.getJuikit(), manager.getScreenManager(), new File(playlist.getFilePath()), playlist, true));
            }
        });
        item.setText(playlist.getFileName());
        return item;
    }
}
