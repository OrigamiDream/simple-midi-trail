package av.is.miditrail.menus.soundfont;

import av.is.miditrail.configurations.SoundfontGroup;
import av.is.miditrail.menus.MenuBarManager;
import av.is.miditrail.menus.MenuItem;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class MenuItemSoundfont extends MenuItem<MenuSoundfont> {

    private final SoundfontGroup soundfont;

    protected MenuItemSoundfont(MenuSoundfont menu, MenuBarManager manager, SoundfontGroup soundfont) {
        super(menu, manager);

        this.soundfont = soundfont;
    }

    @Override
    public JMenuItem createItem() {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                for(SoundfontGroup other : manager.getSoundfontManager().getSoundfonts()) {
                    if(other != soundfont) {
                        other.getMenuItem().setSelected(false);
                    }
                }
                manager.getSoundfontManager().setCurrentSoundfont(soundfont);
            }
        });
        item.setText(soundfont.getId());
        return item;
    }
}
