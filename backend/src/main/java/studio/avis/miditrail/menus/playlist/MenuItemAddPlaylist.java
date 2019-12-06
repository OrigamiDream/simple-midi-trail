package studio.avis.miditrail.menus.playlist;

import studio.avis.miditrail.menus.MenuBarManager;
import studio.avis.miditrail.menus.MenuItem;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;

public class MenuItemAddPlaylist extends MenuItem<MenuPlaylist> {

    MenuItemAddPlaylist(MenuPlaylist menu, MenuBarManager manager) {
        super(menu, manager);
    }

    @Override
    public JMenuItem createItem() {
        JMenuItem item = new JMenuItem(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FileDialog dialog = new FileDialog(manager.getJuikit().frame());
                dialog.setMultipleMode(true);
                dialog.setFilenameFilter((dir, name) -> name.endsWith(".mid") || name.endsWith(".midi"));
                dialog.setVisible(true);

                if(dialog.getFiles() != null && dialog.getFiles().length > 0) {
                    for(File file : dialog.getFiles()) {
                        if(!dialog.getFile().endsWith(".mid") && !dialog.getFile().endsWith(".midi")) {
                            System.out.println("Invalid file format. Only '*.mid' and '*.midi' available.");
                            continue;
                        }
                        manager.getPlaylistManager().addPlaylist(file);
                    }
                    manager.refresh();
                }
            }
        });
        item.setAccelerator(KeyStroke.getKeyStroke('P', Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));;
        item.setText("Add Playlist");
        return item;
    }
}
