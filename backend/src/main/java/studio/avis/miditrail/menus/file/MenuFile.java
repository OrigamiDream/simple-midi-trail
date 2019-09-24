package studio.avis.miditrail.menus.file;

import studio.avis.miditrail.menus.Menu;
import studio.avis.miditrail.menus.MenuBarManager;

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
