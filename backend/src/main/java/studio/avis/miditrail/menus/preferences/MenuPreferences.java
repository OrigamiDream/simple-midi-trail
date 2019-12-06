package studio.avis.miditrail.menus.preferences;

import studio.avis.miditrail.menus.Menu;
import studio.avis.miditrail.menus.MenuBarManager;

import javax.swing.*;

public class MenuPreferences extends Menu {

    public MenuPreferences(MenuBarManager manager) {
        super(manager);
    }

    @Override
    public JMenu createMenu() {
        JMenu menu = new JMenu("Preferences");
        item(menu, new MenuItemPreferenceSummary(this, manager));
        return menu;
    }
}
