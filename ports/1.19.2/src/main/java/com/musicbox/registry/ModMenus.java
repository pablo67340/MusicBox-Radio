package com.musicbox.registry;

import com.musicbox.MusicBox;
import com.musicbox.menu.MusicBoxMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MusicBox.MOD_ID);

    public static final RegistryObject<MenuType<MusicBoxMenu>> MUSIC_BOX =
            MENUS.register("music_box", () -> IForgeMenuType.create(MusicBoxMenu::new));

    private ModMenus() {
    }
}
