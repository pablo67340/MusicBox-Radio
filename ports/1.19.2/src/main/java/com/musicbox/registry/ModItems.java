package com.musicbox.registry;

import com.musicbox.MusicBox;
import com.musicbox.item.HeadphoneMaterial;
import com.musicbox.item.HeadphonesItem;
import com.musicbox.item.SpeakerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MusicBox.MOD_ID);

    public static final RegistryObject<Item> MUSIC_BOX = ITEMS.register("music_box",
            () -> new BlockItem(ModBlocks.MUSIC_BOX.get(), new Item.Properties().tab(MusicBox.TAB)));

    public static final RegistryObject<Item> SPEAKER = ITEMS.register("speaker",
            () -> new SpeakerItem(ModBlocks.SPEAKER.get(), new Item.Properties().tab(MusicBox.TAB)));

    public static final RegistryObject<Item> HEADPHONES = ITEMS.register("headphones",
            () -> new HeadphonesItem(HeadphoneMaterial.INSTANCE,
                    new Item.Properties().tab(MusicBox.TAB).stacksTo(1)));

    private ModItems() {
    }
}
