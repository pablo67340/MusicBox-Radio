package com.musicbox.item;

import com.musicbox.blockentity.MusicBoxBlockEntity;
import com.musicbox.util.Interactions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
//? if >=1.21.2 {
/*public class HeadphonesItem extends net.minecraft.world.item.Item {
*///?} else {
public class HeadphonesItem extends net.minecraft.world.item.ArmorItem {
//?}

    //? if >=1.21.2 {
    /*public HeadphonesItem(Properties properties) {
        super(properties.component(net.minecraft.core.component.DataComponents.EQUIPPABLE, HeadphoneMaterial.equippable()));
    }
    *///?} else if >=1.20.5 {
    public HeadphonesItem(net.minecraft.core.Holder<net.minecraft.world.item.ArmorMaterial> material, Properties properties) {
        super(material, Type.HELMET, properties);
    }
    //?} else if >=1.19.4 {
    /*public HeadphonesItem(net.minecraft.world.item.ArmorMaterial material, Properties properties) {
        super(material, Type.HELMET, properties);
    }
    *///?} else {
    /*public HeadphonesItem(net.minecraft.world.item.ArmorMaterial material, Properties properties) {
        super(material, net.minecraft.world.entity.EquipmentSlot.HEAD, properties);
    }
    *///?}

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
        return Interactions.success(level);
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
    //? if >=1.20.5 {
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    //?} else {
    /*public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
    *///?}
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
