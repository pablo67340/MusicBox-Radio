package com.musicbox.registry;

import com.musicbox.MusicBox;
import com.musicbox.item.HeadphoneMaterial;
import com.musicbox.item.HeadphonesItem;
import com.musicbox.item.SpeakerItem;
import com.musicbox.platform.RegistryRef;
import com.musicbox.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ModItems {

    //? if <1.20 {
    /*// Tabs were plain objects before they became a registry.
    public static final CreativeModeTab TAB =
            Services.PLATFORM.createTab("general", () -> new ItemStack(ModItems.MUSIC_BOX.get()));
    *///?}

    //? if >=1.20.5 && <1.21.2 {
    public static final RegistryRef<net.minecraft.world.item.ArmorMaterial> HEADPHONE_MATERIAL =
            Services.PLATFORM.register(Keys.ARMOR_MATERIAL, HeadphoneMaterial.NAME, HeadphoneMaterial::create);
    //?}

    public static final RegistryRef<Item> MUSIC_BOX = Services.PLATFORM.register(Keys.ITEM, "music_box",
            () -> new BlockItem(ModBlocks.MUSIC_BOX.get(), properties()));

    public static final RegistryRef<Item> SPEAKER = Services.PLATFORM.register(Keys.ITEM, "speaker",
            () -> new SpeakerItem(ModBlocks.SPEAKER.get(), properties()));

    public static final RegistryRef<Item> HEADPHONES = Services.PLATFORM.register(Keys.ITEM, "headphones",
            //? if >=1.20.5 {
            () -> new HeadphonesItem(HEADPHONE_MATERIAL.holder(), properties().stacksTo(1)));
            //?} else {
            /*() -> new HeadphonesItem(HeadphoneMaterial.INSTANCE, properties().stacksTo(1)));
            *///?}

    //? if >=1.20 {
    public static final RegistryRef<CreativeModeTab> TAB = Services.PLATFORM.register(Keys.CREATIVE_MODE_TAB, "main",
            () -> Services.PLATFORM.tabBuilder()
                    .title(Component.translatable("itemGroup." + MusicBox.MOD_ID))
                    .icon(() -> new ItemStack(MUSIC_BOX.get()))
                    .displayItems((params, output) -> {
                        output.accept(MUSIC_BOX.get());
                        output.accept(SPEAKER.get());
                        output.accept(HEADPHONES.get());
                    })
                    .build());
    //?}

    private ModItems() {
    }

    /** Touching the class runs the registrations above. */
    public static void init() {
    }

    private static Item.Properties properties() {
        //? if >=1.20 {
        return new Item.Properties();
        //?} else {
        /*return new Item.Properties().tab(TAB);
        *///?}
    }
}
