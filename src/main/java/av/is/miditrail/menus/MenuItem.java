package av.is.miditrail.menus;

import javax.swing.*;

public abstract class MenuItem<T extends Menu> {

    protected final T menu;
    protected final MenuBarManager manager;

    protected MenuItem(T menu, MenuBarManager manager) {
        this.menu = menu;
        this.manager = manager;
    }

    public abstract JMenuItem createItem();
}
