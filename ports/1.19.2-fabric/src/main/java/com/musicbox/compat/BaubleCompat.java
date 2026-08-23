package com.musicbox.compat;

import com.musicbox.MusicBox;
import com.mojang.datafixers.util.Pair;
import com.musicbox.item.HeadphonesItem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Optional Trinkets support, so headphones can sit in a bauble slot instead of eating the
 * helmet slot.
 * <p>
 * Reached through reflection on purpose, mirroring the Curios integration on the Forge port:
 * headphones already work in the vanilla helmet slot, so an API mismatch should downgrade
 * the feature rather than break the build or the game.
 */
public final class BaubleCompat {

    private static Method getTrinketComponent;
    private static Method getAllEquipped;

    static {
        if (FabricLoader.getInstance().isModLoaded("trinkets")) {
            link();
        }
    }

    private BaubleCompat() {
    }

    public static ItemStack findHeadphones(Player player) {
        if (getTrinketComponent == null) {
            return ItemStack.EMPTY;
        }
        try {
            Object result = getTrinketComponent.invoke(null, player);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) {
                return ItemStack.EMPTY;
            }
            Object component = optional.get();
            if (getAllEquipped == null) {
                getAllEquipped = locate(component.getClass());
                if (getAllEquipped == null) {
                    getTrinketComponent = null;
                    return ItemStack.EMPTY;
                }
            }
            // Entries pair a slot with its stack, and only the stack matters here. The pair
            // type is not worth naming: Trinkets uses DataFixerUpper's Pair, which is easy to
            // confuse with Minecraft's own Tuple, so the stack is picked out by shape instead.
            if (getAllEquipped.invoke(component) instanceof List<?> equipped) {
                for (Object entry : equipped) {
                    ItemStack stack = unwrap(entry);
                    if (stack.getItem() instanceof HeadphonesItem) {
                        return stack;
                    }
                }
            }
        } catch (Throwable t) {
            MusicBox.LOGGER.warn("Trinkets lookup failed; disabling bauble support", t);
            getTrinketComponent = null;
        }
        return ItemStack.EMPTY;
    }

    private static void link() {
        try {
            Class<?> api = Class.forName("dev.emi.trinkets.api.TrinketsApi");
            getTrinketComponent = api.getMethod("getTrinketComponent", LivingEntity.class);
        } catch (Throwable t) {
            MusicBox.LOGGER.warn("Trinkets is installed but its API did not match: {}", t.toString());
        }
    }

    /** Looks the method up on the interfaces first, since the implementing class may be package-private. */
    private static Method locate(Class<?> implementation) {
        for (Class<?> type = implementation; type != null; type = type.getSuperclass()) {
            for (Class<?> candidate : type.getInterfaces()) {
                Method found = scan(candidate);
                if (found != null) {
                    return found;
                }
            }
        }
        return scan(implementation);
    }

    private static Method scan(Class<?> type) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals("getAllEquipped") && method.getParameterCount() == 0) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    /**
     * Pulls the ItemStack out of one slot-and-stack pair.
     * <p>
     * Both halves are checked because Trinkets is not consistent about which way round it
     * builds these, and generics are erased by the time this sees them anyway.
     */
    private static ItemStack unwrap(Object entry) {
        if (entry instanceof Pair<?, ?> pair) {
            if (pair.getSecond() instanceof ItemStack second) {
                return second;
            }
            if (pair.getFirst() instanceof ItemStack first) {
                return first;
            }
        }
        return ItemStack.EMPTY;
    }
}
