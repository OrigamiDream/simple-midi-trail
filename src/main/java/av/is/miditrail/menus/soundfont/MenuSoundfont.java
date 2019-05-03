package av.is.miditrail.menus.soundfont;

import av.is.miditrail.configurations.SoundfontGroup;
import av.is.miditrail.menus.Menu;
import av.is.miditrail.menus.MenuBarManager;

import javax.swing.*;

public class MenuSoundfont extends Menu {

    public MenuSoundfont(MenuBarManager manager) {
        super(manager);
    }

    @Override
    public JMenu createMenu() {
        JMenu menu = new JMenu("Soundfont");
        item(menu, new MenuItemSoundfontAdd(this, manager));
        item(menu, new MenuItemSoundfontDirectoryAdd(this, manager));
        separate(menu);

        for(SoundfontGroup soundfontGroup : manager.getSoundfontManager().getSoundfonts()) {
            MenuItemSoundfont item = new MenuItemSoundfont(this, manager, soundfontGroup);

            JMenuItem menuItem = item(menu, item);
            soundfontGroup.setMenuItem(menuItem);

            if(manager.getSoundfontManager().getCurrentSoundfont().equals(soundfontGroup)) {
                menuItem.setSelected(true);
            }
        }
        return menu;
    }
}
