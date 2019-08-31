package av.is.miditrail.menus.playlist;

import av.is.miditrail.configurations.Playlist;
import av.is.miditrail.menus.MenuBarManager;
import av.is.miditrail.menus.MenuItem;
import av.is.miditrail.screens.TrackScreen;

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
