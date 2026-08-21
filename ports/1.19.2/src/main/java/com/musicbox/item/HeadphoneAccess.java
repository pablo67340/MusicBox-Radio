package com.musicbox.item;

import com.musicbox.compat.BaubleCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Finds the headphones a player is wearing - helmet slot or a bauble slot - and pairs them. */
public final class HeadphoneAccess {

    public enum PairResult {
        NONE_FOUND,
        PAIRED,
        UNPAIRED
    }

    private HeadphoneAccess() {
    }

    public static ItemStack findWorn(Player player) {
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.getItem() instanceof HeadphonesItem) {
            return helmet;
        }
        return BaubleCompat.findHeadphones(player);
    }

    public static boolean isWearing(Player player) {
        return !findWorn(player).isEmpty();
    }

    /** Worn headphones win, so pairing works the same whether or not your hands are busy. */
    public static ItemStack findWornOrHeld(Player player) {
        ItemStack worn = findWorn(player);
        if (!worn.isEmpty()) {
            return worn;
        }
        for (ItemStack held : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (held.getItem() instanceof HeadphonesItem) {
                return held;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Pairs to the box, or unpairs if these headphones were already on it. Server side only. */
    public static PairResult togglePairing(Player player, ResourceLocation dimension, BlockPos pos) {
        ItemStack stack = findWornOrHeld(player);
        if (stack.isEmpty()) {
            return PairResult.NONE_FOUND;
        }

        PairResult result;
        if (HeadphonesItem.isPairedTo(stack, dimension, pos)) {
            HeadphonesItem.clear(stack);
            result = PairResult.UNPAIRED;
        } else {
            HeadphonesItem.bind(stack, dimension, pos);
            result = PairResult.PAIRED;
        }

        // The music box menu has no slots, so an open GUI would otherwise starve the player's
        // own inventory of updates and leave the headphone tooltip stale.
        player.inventoryMenu.broadcastChanges();
        return result;
    }

    public static void announce(Player player, PairResult result, BlockPos pos) {
        switch (result) {
            case NONE_FOUND -> player.displayClientMessage(
                    Component.translatable("message.musicboxradio.no_headphones"), true);
            case PAIRED -> {
                player.displayClientMessage(Component.translatable("message.musicboxradio.headphones_bound",
                        pos.getX(), pos.getY(), pos.getZ()), true);
                ping(player, pos, 1.6F);
            }
            case UNPAIRED -> {
                player.displayClientMessage(Component.translatable("message.musicboxradio.headphones_unbound"), true);
                ping(player, pos, 0.8F);
            }
        }
    }

    private static void ping(Player player, BlockPos pos, float pitch) {
        player.level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING, SoundSource.BLOCKS, 0.6F, pitch);
    }
}
