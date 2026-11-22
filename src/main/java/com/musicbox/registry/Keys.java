package com.musicbox.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Registry keys, which moved out of {@code Registry} in 1.19.3. */
public final class Keys {

    //? if >=1.19.3 {
    public static final ResourceKey<Registry<Block>> BLOCK = net.minecraft.core.registries.Registries.BLOCK;
    public static final ResourceKey<Registry<Item>> ITEM = net.minecraft.core.registries.Registries.ITEM;
    public static final ResourceKey<Registry<BlockEntityType<?>>> BLOCK_ENTITY_TYPE =
            net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE;
    public static final ResourceKey<Registry<MenuType<?>>> MENU = net.minecraft.core.registries.Registries.MENU;
    public static final ResourceKey<Registry<net.minecraft.world.item.CreativeModeTab>> CREATIVE_MODE_TAB =
            net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB;
    //?} else {
    /*public static final ResourceKey<Registry<Block>> BLOCK = Registry.BLOCK_REGISTRY;
    public static final ResourceKey<Registry<Item>> ITEM = Registry.ITEM_REGISTRY;
    public static final ResourceKey<Registry<BlockEntityType<?>>> BLOCK_ENTITY_TYPE = Registry.BLOCK_ENTITY_TYPE_REGISTRY;
    public static final ResourceKey<Registry<MenuType<?>>> MENU = Registry.MENU_REGISTRY;
    *///?}

    //? if >=1.20.5 && <1.21.2 {
    public static final ResourceKey<Registry<net.minecraft.world.item.ArmorMaterial>> ARMOR_MATERIAL =
            net.minecraft.core.registries.Registries.ARMOR_MATERIAL;
    //?}

    private Keys() {
    }
}
