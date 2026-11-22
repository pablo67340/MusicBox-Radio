package com.musicbox.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The music box a headphone or speaker stack is paired to. From 1.20.5 it lives in the
 * stack's custom data under the same keys older versions used in item NBT, so the vanilla
 * upgrade from item tags to custom data carries old pairings across.
 */
public final class PairingLink {

    private static final String TAG_ROOT = "MusicBoxLink";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_POS = "Pos";

    private PairingLink() {
    }

    public static void bind(ItemStack stack, ResourceLocation dimension, BlockPos pos) {
        CompoundTag root = new CompoundTag();
        root.putString(TAG_DIMENSION, dimension.toString());
        root.putLong(TAG_POS, pos.asLong());
        //? if >=1.20.5 {
        net.minecraft.world.item.component.CustomData.update(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, tag -> tag.put(TAG_ROOT, root));
        //?} else {
        /*stack.getOrCreateTag().put(TAG_ROOT, root);
        *///?}
    }

    public static void clear(ItemStack stack) {
        //? if >=1.20.5 {
        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
            net.minecraft.world.item.component.CustomData.update(
                    net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(TAG_ROOT));
        }
        //?} else {
        /*CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(TAG_ROOT);
            if (tag.isEmpty()) {
                stack.setTag(null);
            }
        }
        *///?}
    }

    public static boolean isPaired(ItemStack stack) {
        return root(stack) != null;
    }

    @Nullable
    public static ResourceLocation boundDimension(ItemStack stack) {
        CompoundTag root = root(stack);
        return root == null ? null : ResourceLocation.tryParse(root.getString(TAG_DIMENSION));
    }

    @Nullable
    public static BlockPos boundPos(ItemStack stack) {
        CompoundTag root = root(stack);
        return root == null ? null : BlockPos.of(root.getLong(TAG_POS));
    }

    public static boolean isPairedTo(ItemStack stack, ResourceLocation dimension, BlockPos pos) {
        return dimension.equals(boundDimension(stack)) && pos.equals(boundPos(stack));
    }

    @Nullable
    private static CompoundTag root(ItemStack stack) {
        //? if >=1.20.5 {
        net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(TAG_ROOT)) {
            return null;
        }
        return data.copyTag().getCompound(TAG_ROOT);
        //?} else {
        /*CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(TAG_ROOT) ? null : tag.getCompound(TAG_ROOT);
        *///?}
    }
}
