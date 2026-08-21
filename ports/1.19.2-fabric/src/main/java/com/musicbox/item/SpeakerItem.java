package com.musicbox.item;

import com.musicbox.blockentity.MusicBoxBlockEntity;
import com.musicbox.blockentity.SpeakerBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The speaker in item form. Use it on a music box to pair it before placing, the same
 * gesture that pairs headphones; the placed block inherits the link from the stack, so a
 * stack of speakers can be paired once and then put up around a room.
 */
public class SpeakerItem extends BlockItem {

    private static final String TAG_ROOT = "MusicBoxLink";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_POS = "Pos";

    public SpeakerItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof MusicBoxBlockEntity && player != null) {
            // Pairing beats placing: clicking a box with a speaker in hand is the gesture the
            // headphones already use, and placing a speaker into the box's own face is never
            // what someone meant by it.
            if (!level.isClientSide) {
                ItemStack stack = context.getItemInHand();
                boolean unpair = isPairedTo(stack, level.dimension().location(), pos);
                if (unpair) {
                    clear(stack);
                    player.displayClientMessage(
                            Component.translatable("message.musicboxradio.speaker.unpaired"), true);
                } else {
                    bind(stack, level.dimension().location(), pos);
                    player.displayClientMessage(
                            Component.translatable("message.musicboxradio.speaker.paired",
                                    pos.getX(), pos.getY(), pos.getZ()), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.useOn(context);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player,
                                                 ItemStack stack, BlockState state) {
        boolean handled = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker) {
            ResourceLocation dimension = boundDimension(stack);
            BlockPos bound = boundPos(stack);
            if (dimension != null && bound != null) {
                speaker.bind(dimension, bound);
            }
        }
        return handled;
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
            tooltip.add(Component.translatable("tooltip.musicboxradio.speaker_bound",
                            pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.musicboxradio.speaker_unbound")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.musicboxradio.speaker_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
