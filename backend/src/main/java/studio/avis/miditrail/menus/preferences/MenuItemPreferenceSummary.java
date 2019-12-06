package studio.avis.miditrail.menus.preferences;

import studio.avis.miditrail.menus.MenuBarManager;
import studio.avis.miditrail.menus.MenuItem;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class MenuItemPreferenceSummary extends MenuItem<MenuPreferences> {

    MenuItemPreferenceSummary(MenuPreferences menu, MenuBarManager manager) {
        super(menu, manager);
    }

    @Override
    public JMenuItem createItem() {
        JMenuItem item = new JMenuItem(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                manager.getScreenManager().getPreferenceManager().setSummary(!manager.getScreenManager().getPreferenceManager().isSummaryEnabled());
                ((JMenuItem) e.getSource()).setSelected(manager.getScreenManager().getPreferenceManager().isSummaryEnabled());
            }
        });
        item.setAccelerator(KeyStroke.getKeyStroke('B', Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        item.setText("Show Summary");
        item.setSelected(manager.getScreenManager().getPreferenceManager().isSummaryEnabled());
        return item;
    }
}
