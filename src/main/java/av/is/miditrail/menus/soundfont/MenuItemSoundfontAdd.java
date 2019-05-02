package av.is.miditrail.menus.soundfont;

import av.is.miditrail.Soundfonts;
import av.is.miditrail.menus.MenuBarManager;
import av.is.miditrail.menus.MenuItem;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;

public class MenuItemSoundfontAdd extends MenuItem<MenuSoundfont> {

    protected MenuItemSoundfontAdd(MenuSoundfont menu, MenuBarManager manager) {
        super(menu, manager);
    }

    @Override
    public JMenuItem createItem() {
        JMenuItem item = new JMenuItem(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FileDialog dialog = new FileDialog(manager.getJuikit().frame());
                dialog.setFilenameFilter((dir, name) -> name.endsWith(".sf2"));
                dialog.setVisible(true);

                if(dialog.getFile() != null) {
                    Soundfonts.addSoundfont(manager.getConfiguration(), new File(dialog.getDirectory() + dialog.getFile()));
                    manager.getConfiguration().save();
                    manager.refresh();
                }
            }
        });
        item.setAccelerator(KeyStroke.getKeyStroke('D', Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        item.setText("Add Soundfont...");
        return item;
    }
}
