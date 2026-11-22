package com.musicbox.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Optional bauble slot support, so headphones can sit in a Curios or Trinkets slot instead of
 * eating the helmet slot.
 * <p>
 * Both are reached through reflection on purpose. Neither is published somewhere every
 * version can reliably compile against, and headphones already work in the vanilla helmet
 * slot, so an API mismatch should downgrade the feature rather than break the build or the game.
 */
public final class BaubleCompat {

    private BaubleCompat() {
    }

    public static ItemStack findHeadphones(Player player) {
        ItemStack curio = CuriosCompat.findHeadphones(player);
        return curio.isEmpty() ? TrinketsCompat.findHeadphones(player) : curio;
    }
}
