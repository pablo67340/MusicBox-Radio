package com.musicbox.registry;

import com.musicbox.MusicBox;
import com.musicbox.item.HeadphoneMaterial;
import com.musicbox.item.HeadphonesItem;
import net.minecraft.core.Registry;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class ModItems {

    public static final Item MUSIC_BOX = Registry.register(Registry.ITEM, MusicBox.id("music_box"),
            new BlockItem(ModBlocks.MUSIC_BOX, new Item.Properties().tab(MusicBox.TAB)));

    public static final Item HEADPHONES = Registry.register(Registry.ITEM, MusicBox.id("headphones"),
            new HeadphonesItem(HeadphoneMaterial.INSTANCE,
                    new Item.Properties().tab(MusicBox.TAB).stacksTo(1)));

    private ModItems() {
    }

    public static void register() {
    }
}
