package studio.avis.miditrail.menus.playlist;

import studio.avis.miditrail.configurations.Playlist;
import studio.avis.miditrail.menus.Menu;
import studio.avis.miditrail.menus.MenuBarManager;

import javax.swing.*;

public class MenuPlaylist extends Menu {

    public MenuPlaylist(MenuBarManager manager) {
        super(manager);
    }

    @Override
    public JMenu createMenu() {
        JMenu menu = new JMenu("Playlist");
        item(menu, new MenuItemAddPlaylist(this, manager));
        item(menu, new MenuItemClearPlaylist(this, manager));
        separate(menu);

        for(Playlist playlist : manager.getPlaylistManager().getPlaylists()) {
            MenuItemPlaylist item = new MenuItemPlaylist(this, manager, playlist);
            item(menu, item);
        }
        return menu;
    }
}
