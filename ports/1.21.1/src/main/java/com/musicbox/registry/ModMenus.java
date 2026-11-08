package com.musicbox.registry;

import com.musicbox.MusicBox;
import com.musicbox.menu.MusicBoxMenu;
import com.musicbox.menu.SpeakerMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Consumer;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MusicBox.MOD_ID);

    public static final RegistryObject<MenuType<MusicBoxMenu>> MUSIC_BOX =
            MENUS.register("music_box", () -> IForgeMenuType.create(MusicBoxMenu::new));

    public static final RegistryObject<MenuType<SpeakerMenu>> SPEAKER =
            MENUS.register("speaker", () -> IForgeMenuType.create(SpeakerMenu::new));

    private ModMenus() {
    }

    /** Opens a menu whose client side is built from {@code openData}. */
    public static void open(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> openData) {
        player.openMenu(provider, openData);
    }
}
