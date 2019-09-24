package studio.avis.miditrail.menus.soundfont;

import studio.avis.miditrail.menus.MenuBarManager;
import studio.avis.miditrail.menus.MenuItem;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;

public class MenuItemSoundfontDirectoryAdd extends MenuItem<MenuSoundfont> {

    protected MenuItemSoundfontDirectoryAdd(MenuSoundfont menu, MenuBarManager manager) {
        super(menu, manager);
    }

    @Override
    public JMenuItem createItem() {
        JMenuItem item = new JMenuItem(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(manager.getJuikit().macOS()) {
                    System.setProperty("apple.awt.fileDialogForDirectories", "true");
                }
                FileDialog dialog = new FileDialog(manager.getJuikit().frame());
                dialog.setVisible(true);

                if(dialog.getFile() != null) {
                    File directory = new File(dialog.getDirectory() + dialog.getFile());
                    manager.getSoundfontManager().addSoundfontDirectory(directory);
                    manager.refresh();
                }
                if(manager.getJuikit().macOS()) {
                    System.setProperty("apple.awt.fileDialogForDirectories", "false");
                }
            }
        });
        item.setAccelerator(KeyStroke.getKeyStroke('G', Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        item.setText("Add Soundfont Directory...");
        return item;
    }
}
