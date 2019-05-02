package av.is.miditrail.menus.soundfont;

import av.is.miditrail.Soundfonts;
import av.is.miditrail.menus.MenuBarManager;
import av.is.miditrail.menus.MenuItem;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class MenuItemSoundfont extends MenuItem<MenuSoundfont> {

    private final Soundfonts.Soundfont soundfont;

    protected MenuItemSoundfont(MenuSoundfont menu, MenuBarManager manager, Soundfonts.Soundfont soundfont) {
        super(menu, manager);
        this.soundfont = soundfont;
    }

    @Override
    public JMenuItem createItem() {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                for(Soundfonts.Soundfont other : Soundfonts.getSoundfonts()) {
                    if(other != soundfont) {
                        other.menuItem.setSelected(false);
                    }
                }
                Soundfonts.CURRENT_SOUNDFONT.set(soundfont);
            }
        });
        item.setText(soundfont.name);
        return item;
    }
}
