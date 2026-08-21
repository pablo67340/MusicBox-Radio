package com.musicbox.item;

import com.musicbox.blockentity.MusicBoxBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Wearable headphones. Pair them to a music box and the server pipes that box straight to the
 * listener in stereo, anywhere in the world, in whatever dimension. Unpaired headphones just
 * follow whichever box is playing nearest.
 */
public class HeadphonesItem extends ArmorItem {

    private static final String TAG_ROOT = "MusicBoxLink";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_POS = "Pos";

    public HeadphonesItem(ArmorMaterial material, Properties properties) {
        super(material, EquipmentSlot.HEAD, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        Player player = context.getPlayer();
        if (!(be instanceof MusicBoxBlockEntity) || player == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            HeadphoneAccess.announce(player,
                    HeadphoneAccess.togglePairing(player, level.dimension().location(), pos), pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static void bind(ItemStack stack, ResourceLocation dimension, BlockPos pos) {
        CompoundTag root = new CompoundTag();
        root.putString(TAG_DIMENSION, dimension.toString());
        root.putLong(TAG_POS, pos.asLong());
        stack.getOrCreateTag().put(TAG_ROOT, root);
    }

    public static void clear(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(TAG_ROOT);
            if (tag.isEmpty()) {
                stack.setTag(null);
            }
        }
    }

    public static boolean isPaired(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_ROOT);
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
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(TAG_ROOT) ? null : tag.getCompound(TAG_ROOT);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        BlockPos pos = boundPos(stack);
        if (pos != null) {
            tooltip.add(Component.translatable("tooltip.musicboxradio.headphones_bound",
                            pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.musicboxradio.headphones_unbound")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.musicboxradio.headphones_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
