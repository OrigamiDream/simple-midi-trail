package studio.avis.miditrail.menus.playlist;

import studio.avis.miditrail.menus.MenuBarManager;
import studio.avis.miditrail.menus.MenuItem;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class MenuItemClearPlaylist extends MenuItem<MenuPlaylist> {

    MenuItemClearPlaylist(MenuPlaylist menu, MenuBarManager manager) {
        super(menu, manager);
    }

    @Override
    public JMenuItem createItem() {
        JMenuItem item = new JMenuItem(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                manager.getPlaylistManager().clearPlaylist();
                manager.refresh();
            }
        });
        item.setText("Clear Playlists");
        return item;
    }
}
