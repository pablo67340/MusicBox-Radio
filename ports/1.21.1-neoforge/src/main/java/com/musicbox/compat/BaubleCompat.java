package com.musicbox.compat;

import com.musicbox.MusicBox;
import com.musicbox.item.HeadphonesItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Optional Curios support, so headphones can sit in a bauble slot instead of eating the
 * helmet slot.
 * <p>
 * Reached through reflection on purpose. Curios is not published to a maven repository we
 * can reliably compile against, and headphones already work in the vanilla helmet slot, so
 * an API mismatch should downgrade the feature rather than break the build or the game.
 * <p>
 * The head slot itself comes from data/musicboxradio/curios/entities/head.json; Curios
 * dropped IMC slot requests in favour of datapacks.
 */
public final class BaubleCompat {

    private static final Predicate<ItemStack> IS_HEADPHONES = stack -> stack.getItem() instanceof HeadphonesItem;

    private static Method getCuriosInventory;

    static {
        if (ModList.get().isLoaded("curios")) {
            link();
        }
    }

    private BaubleCompat() {
    }

    public static ItemStack findHeadphones(Player player) {
        if (getCuriosInventory == null) {
            return ItemStack.EMPTY;
        }
        try {
            if (!(getCuriosInventory.invoke(null, player) instanceof Optional<?> inventory) || inventory.isEmpty()) {
                return ItemStack.EMPTY;
            }
            Object handler = inventory.get();
            Method findFirstCurio = findMethod(handler.getClass(), "findFirstCurio", Predicate.class);
            if (findFirstCurio == null) {
                return ItemStack.EMPTY;
            }
            Object result = findFirstCurio.invoke(handler, IS_HEADPHONES);
            if (result instanceof Optional<?> optional && optional.isPresent()) {
                return unwrap(optional.get());
            }
        } catch (Throwable t) {
            MusicBox.LOGGER.warn("Curios lookup failed; disabling bauble support", t);
            getCuriosInventory = null;
        }
        return ItemStack.EMPTY;
    }

    private static void link() {
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            getCuriosInventory = api.getMethod("getCuriosInventory", LivingEntity.class);
        } catch (Throwable t) {
            MusicBox.LOGGER.warn("Curios is installed but its API did not match; "
                    + "headphones will only work in the helmet slot: {}", t.toString());
        }
    }

    /** Looks the method up on the interfaces first, since the implementing class may be package-private. */
    private static Method findMethod(Class<?> implementation, String name, Class<?> param) {
        for (Class<?> type = implementation; type != null; type = type.getSuperclass()) {
            for (Class<?> candidate : type.getInterfaces()) {
                try {
                    return candidate.getMethod(name, param);
                } catch (NoSuchMethodException ignored) {
                    // keep looking
                }
            }
        }
        try {
            Method method = implementation.getMethod(name, param);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** Pulls the ItemStack out of a Curios {@code SlotResult} without naming the type. */
    private static ItemStack unwrap(Object slotResult) throws Exception {
        for (Method method : slotResult.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType() == ItemStack.class) {
                method.setAccessible(true);
                Object stack = method.invoke(slotResult);
                if (stack instanceof ItemStack itemStack) {
                    return itemStack;
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
