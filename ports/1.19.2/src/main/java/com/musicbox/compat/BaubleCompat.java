package com.musicbox.compat;

import com.musicbox.MusicBox;
import com.musicbox.item.HeadphonesItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

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
 */
public final class BaubleCompat {

    private static final Predicate<ItemStack> IS_HEADPHONES = stack -> stack.getItem() instanceof HeadphonesItem;

    private static Object helper;
    private static Method findFirstCurio;

    static {
        if (ModList.get().isLoaded("curios")) {
            link();
        }
    }

    private BaubleCompat() {
    }

    public static ItemStack findHeadphones(Player player) {
        if (findFirstCurio == null) {
            return ItemStack.EMPTY;
        }
        try {
            Object result = findFirstCurio.invoke(helper, player, IS_HEADPHONES);
            if (result instanceof Optional<?> optional && optional.isPresent()) {
                return unwrap(optional.get());
            }
        } catch (Throwable t) {
            MusicBox.LOGGER.warn("Curios lookup failed; disabling bauble support", t);
            findFirstCurio = null;
        }
        return ItemStack.EMPTY;
    }

    private static void link() {
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            helper = api.getMethod("getCuriosHelper").invoke(null);
            findFirstCurio = locate(helper.getClass());
            if (findFirstCurio == null) {
                MusicBox.LOGGER.warn("Curios is installed but findFirstCurio was not found; "
                        + "headphones will only work in the helmet slot");
            }
        } catch (Throwable t) {
            MusicBox.LOGGER.warn("Curios is installed but its API did not match: {}", t.toString());
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
            if (!method.getName().equals("findFirstCurio") || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params[0].isAssignableFrom(LivingEntity.class) && params[1] == Predicate.class) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
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
