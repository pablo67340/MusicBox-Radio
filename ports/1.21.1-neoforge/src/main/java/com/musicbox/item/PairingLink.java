package com.musicbox.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * The music box a headphone or speaker stack is paired to, kept in the stack's custom data
 * under the same keys the 1.19/1.20 builds used in item NBT, so the vanilla upgrade from
 * item tags to custom data carries old pairings across.
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
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(TAG_ROOT, root));
    }

    public static void clear(ItemStack stack) {
        if (stack.has(DataComponents.CUSTOM_DATA)) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(TAG_ROOT));
        }
    }

    public static boolean isPaired(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains(TAG_ROOT);
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
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(TAG_ROOT)) {
            return null;
        }
        return data.copyTag().getCompound(TAG_ROOT);
    }
}
