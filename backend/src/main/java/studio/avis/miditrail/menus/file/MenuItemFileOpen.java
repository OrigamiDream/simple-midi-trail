package studio.avis.miditrail.menus.file;

import studio.avis.miditrail.menus.MenuBarManager;
import studio.avis.miditrail.menus.MenuItem;
import studio.avis.miditrail.screens.TrackScreen;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;

public class MenuItemFileOpen extends MenuItem<MenuFile> {

    MenuItemFileOpen(MenuFile menu, MenuBarManager manager) {
        super(menu, manager);
    }

    @Override
    public JMenuItem createItem() {
        JMenuItem item = new JMenuItem(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FileDialog dialog = new FileDialog(manager.getJuikit().frame());
                dialog.setFilenameFilter((dir, name) -> name.endsWith(".mid") || name.endsWith(".midi"));
                dialog.setVisible(true);

                if(dialog.getFile() != null) {
                    if(!dialog.getFile().endsWith(".mid") && !dialog.getFile().endsWith(".midi")) {
                        System.out.println("Invalid file format. Only '*.mid' and '*.midi' available.");
                        return;
                    }
                    manager.getScreenManager().setScreen(new TrackScreen(manager.getJuikit(), manager.getScreenManager(), new File(dialog.getDirectory() + dialog.getFile())));
                }
            }
        });
        item.setAccelerator(KeyStroke.getKeyStroke('O', Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        item.setText("Open...");
        return item;
    }
}
