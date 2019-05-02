package av.is.miditrail.menus;

import javax.swing.*;

public abstract class Menu {

    protected final MenuBarManager manager;

    protected Menu(MenuBarManager manager) {
        this.manager = manager;
    }

    public abstract JMenu createMenu();

    protected JMenuItem item(JMenu menu, MenuItem menuItem) {
        JMenuItem item = menuItem.createItem();
        menu.add(item);
        return item;
    }

    protected void separate(JMenu menu) {
        menu.addSeparator();
    }
}
