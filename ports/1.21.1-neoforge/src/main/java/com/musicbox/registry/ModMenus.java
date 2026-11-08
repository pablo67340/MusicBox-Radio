package com.musicbox.registry;

import com.musicbox.MusicBox;
import com.musicbox.menu.MusicBoxMenu;
import com.musicbox.menu.SpeakerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Consumer;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MusicBox.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<MusicBoxMenu>> MUSIC_BOX =
            MENUS.register("music_box", () -> IMenuTypeExtension.create(MusicBoxMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<SpeakerMenu>> SPEAKER =
            MENUS.register("speaker", () -> IMenuTypeExtension.create(SpeakerMenu::new));

    private ModMenus() {
    }

    /** Opens a menu whose client side is built from {@code openData}. */
    public static void open(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> openData) {
        player.openMenu(provider, openData::accept);
    }
}
