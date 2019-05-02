package av.is.miditrail.menus.soundfont;

import av.is.miditrail.Soundfonts;
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
        separate(menu);

        boolean manual = false;
        for(Soundfonts.Soundfont soundfont : Soundfonts.getSoundfonts()) {
            MenuItemSoundfont item = new MenuItemSoundfont(this, manager, soundfont);

            if(soundfont.file && !manual) {
                manual = true;
                separate(menu);
            }

            JMenuItem menuItem = item(menu, item);
            soundfont.menuItem = menuItem;

            if(Soundfonts.CURRENT_SOUNDFONT.get().equals(soundfont)) {
                menuItem.setSelected(true);
            }
        }
        return menu;
    }
}
