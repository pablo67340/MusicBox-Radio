package com.musicbox.registry;

import com.musicbox.MusicBox;
import com.musicbox.item.HeadphoneMaterial;
import com.musicbox.item.HeadphonesItem;
import com.musicbox.item.SpeakerItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, MusicBox.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MusicBox.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MusicBox.MOD_ID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> HEADPHONE_MATERIAL =
            ARMOR_MATERIALS.register(HeadphoneMaterial.NAME, HeadphoneMaterial::create);

    public static final DeferredHolder<Item, Item> MUSIC_BOX = ITEMS.register("music_box",
            () -> new BlockItem(ModBlocks.MUSIC_BOX.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> SPEAKER = ITEMS.register("speaker",
            () -> new SpeakerItem(ModBlocks.SPEAKER.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> HEADPHONES = ITEMS.register("headphones",
            () -> new HeadphonesItem(HEADPHONE_MATERIAL, new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MusicBox.MOD_ID))
                    .icon(() -> new ItemStack(MUSIC_BOX.get()))
                    .displayItems((params, output) -> {
                        output.accept(MUSIC_BOX.get());
                        output.accept(SPEAKER.get());
                        output.accept(HEADPHONES.get());
                    })
                    .build());

    private ModItems() {
    }
}
