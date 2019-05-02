package av.is.miditrail.menus.file;

import av.is.miditrail.menus.Menu;
import av.is.miditrail.menus.MenuBarManager;

import javax.swing.*;

public class MenuFile extends Menu {

    public MenuFile(MenuBarManager manager) {
        super(manager);
    }

    @Override
    public JMenu createMenu() {
        JMenu menu = new JMenu("File");
        item(menu, new MenuItemFileOpen(this, manager));
        return menu;
    }
}
