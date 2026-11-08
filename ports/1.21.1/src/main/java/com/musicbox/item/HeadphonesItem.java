package com.musicbox.item;

import com.musicbox.blockentity.MusicBoxBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
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

    public HeadphonesItem(Holder<ArmorMaterial> material, Properties properties) {
        super(material, Type.HELMET, properties);
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
        PairingLink.bind(stack, dimension, pos);
    }

    public static void clear(ItemStack stack) {
        PairingLink.clear(stack);
    }

    public static boolean isPaired(ItemStack stack) {
        return PairingLink.isPaired(stack);
    }

    @Nullable
    public static ResourceLocation boundDimension(ItemStack stack) {
        return PairingLink.boundDimension(stack);
    }

    @Nullable
    public static BlockPos boundPos(ItemStack stack) {
        return PairingLink.boundPos(stack);
    }

    public static boolean isPairedTo(ItemStack stack, ResourceLocation dimension, BlockPos pos) {
        return PairingLink.isPairedTo(stack, dimension, pos);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
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
