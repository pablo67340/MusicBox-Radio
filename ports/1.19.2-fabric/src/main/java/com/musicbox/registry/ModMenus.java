package com.musicbox.registry;

import com.musicbox.MusicBox;
import com.musicbox.menu.MusicBoxMenu;
import com.musicbox.menu.SpeakerMenu;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {

    /**
     * Extended so the station list can ride along in the screen-opening packet; the client
     * never reads the server's config file.
     */
    public static final MenuType<MusicBoxMenu> MUSIC_BOX = Registry.register(
            Registry.MENU, MusicBox.id("music_box"),
            new ExtendedScreenHandlerType<>(MusicBoxMenu::new));

    /** Extended too: the speaker's list of nearby boxes is resolved server side. */
    public static final MenuType<SpeakerMenu> SPEAKER = Registry.register(
            Registry.MENU, MusicBox.id("speaker"),
            new ExtendedScreenHandlerType<>(SpeakerMenu::new));

    private ModMenus() {
    }

    public static void register() {
    }
}
