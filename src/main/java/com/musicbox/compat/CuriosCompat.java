package com.musicbox.compat;

import com.musicbox.MusicBox;
import com.musicbox.item.HeadphonesItem;
import com.musicbox.platform.Services;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Curios, on Forge and NeoForge.
 * <p>
 * Curios changed how a slot is searched in 1.20: older builds hang {@code findFirstCurio}
 * off a global helper, newer ones off the entity's own inventory. Both are tried, so one
 * class covers every version. The head slot itself comes from
 * data/musicboxradio/curios/entities/head.json on Curios builds that read slots from data,
 * and from {@link #headSlotRequest()} over IMC on the ones from before that.
 */
public final class CuriosCompat {

    private static final Predicate<ItemStack> IS_HEADPHONES = stack -> stack.getItem() instanceof HeadphonesItem;

    /** {@code CuriosApi.getCuriosInventory(LivingEntity)}, 1.20 and later. */
    private static Method getCuriosInventory;
    /** {@code CuriosApi.getCuriosHelper()} and its {@code findFirstCurio(LivingEntity, Predicate)}, before 1.20. */
    private static Object helper;
    private static Method helperFindFirst;

    static {
        if (Services.PLATFORM.isModLoaded("curios")) {
            link();
        }
    }

    private CuriosCompat() {
    }

    public static ItemStack findHeadphones(Player player) {
        try {
            if (getCuriosInventory != null) {
                if (!(getCuriosInventory.invoke(null, player) instanceof Optional<?> inventory) || inventory.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                Object handler = inventory.get();
                Method findFirstCurio = findMethod(handler.getClass(), "findFirstCurio", Predicate.class);
                return findFirstCurio == null ? ItemStack.EMPTY : unwrap(findFirstCurio.invoke(handler, IS_HEADPHONES));
            }
            if (helperFindFirst != null) {
                return unwrap(helperFindFirst.invoke(helper, player, IS_HEADPHONES));
            }
        } catch (Throwable t) {
            MusicBox.LOGGER.warn("Curios lookup failed; disabling bauble support", t);
            getCuriosInventory = null;
            helperFindFirst = null;
        }
        return ItemStack.EMPTY;
    }

    /**
     * The IMC method and message asking Curios for a head slot, or null on Curios builds that
     * take slots from data instead.
     * <p>
     * Curios ships no slots of its own; a slot only exists if something asks for one. Without
     * this the headphones would have nowhere to go in a pack where nothing else asked, which
     * looks like the integration is broken. Asking twice is harmless, as Curios merges requests.
     */
    public static Map.Entry<String, Object> headSlotRequest() {
        try {
            Class<?> presets = Class.forName("top.theillusivec4.curios.api.SlotTypePreset");
            Object head = presets.getField("HEAD").get(null);
            Object builder = presets.getMethod("getMessageBuilder").invoke(head);
            Object message = builder.getClass().getMethod("build").invoke(builder);
            String method = (String) Class.forName("top.theillusivec4.curios.api.SlotTypeMessage")
                    .getField("REGISTER_TYPE").get(null);
            return Map.entry(method, message);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Throwable t) {
            MusicBox.LOGGER.warn("Could not ask Curios for a head slot: {}", t.toString());
            return null;
        }
    }

    private static void link() {
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            try {
                getCuriosInventory = api.getMethod("getCuriosInventory", LivingEntity.class);
                return;
            } catch (NoSuchMethodException ignored) {
                // An older Curios; fall through to the helper.
            }
            helper = api.getMethod("getCuriosHelper").invoke(null);
            helperFindFirst = findMethod(helper.getClass(), "findFirstCurio", LivingEntity.class, Predicate.class);
            if (helperFindFirst == null) {
                MusicBox.LOGGER.warn("Curios is installed but findFirstCurio was not found; "
                        + "headphones will only work in the helmet slot");
            }
        } catch (Throwable t) {
            MusicBox.LOGGER.warn("Curios is installed but its API did not match; "
                    + "headphones will only work in the helmet slot: {}", t.toString());
        }
    }

    /** Looks the method up on the interfaces first, since the implementing class may be package-private. */
    private static Method findMethod(Class<?> implementation, String name, Class<?>... params) {
        for (Class<?> type = implementation; type != null; type = type.getSuperclass()) {
            for (Class<?> candidate : type.getInterfaces()) {
                try {
                    return candidate.getMethod(name, params);
                } catch (NoSuchMethodException ignored) {
                    // keep looking
                }
            }
        }
        try {
            Method method = implementation.getMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** Pulls the ItemStack out of an {@code Optional<SlotResult>} without naming the type. */
    private static ItemStack unwrap(Object result) throws Exception {
        if (!(result instanceof Optional<?> optional) || optional.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Object slotResult = optional.get();
        for (Method method : slotResult.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType() == ItemStack.class) {
                method.setAccessible(true);
                if (method.invoke(slotResult) instanceof ItemStack stack) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
