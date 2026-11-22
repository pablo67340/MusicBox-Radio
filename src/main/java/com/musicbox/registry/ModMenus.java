package com.musicbox.registry;

import com.musicbox.menu.MusicBoxMenu;
import com.musicbox.menu.SpeakerMenu;
import com.musicbox.platform.Services;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Supplier;

public final class ModMenus {

    /** The station list rides along in the opening packet; the client never reads the server's config. */
    public static final Supplier<MenuType<MusicBoxMenu>> MUSIC_BOX =
            Services.PLATFORM.registerMenu("music_box", MusicBoxMenu::new);

    /** The speaker's list of nearby boxes is resolved server side and sent the same way. */
    public static final Supplier<MenuType<SpeakerMenu>> SPEAKER =
            Services.PLATFORM.registerMenu("speaker", SpeakerMenu::new);

    private ModMenus() {
    }

    /** Touching the class runs the registrations above. */
    public static void init() {
    }
}
