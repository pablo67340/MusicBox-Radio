package com.musicbox.platform;

import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Everything the mod needs from its loader. Gameplay code only talks to this, so the same
 * sources build for Fabric, Forge and NeoForge; each loader's implementation lives in its own
 * package under {@code com.musicbox.platform} and only that one is compiled into a jar.
 */
public interface Platform {

    Path configDir();

    boolean isModLoaded(String modId);

    /**
     * Registers {@code factory} under the mod's namespace. The value may not exist until the
     * loader's registration phase, so callers keep the returned reference and call it later.
     */
    <T> RegistryRef<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<T> factory);

    //? if >=1.20 {
    /** A creative tab builder; the vanilla one needs a row and column the loaders assign themselves. */
    net.minecraft.world.item.CreativeModeTab.Builder tabBuilder();
    //?} else {
    /*net.minecraft.world.item.CreativeModeTab createTab(String name, Supplier<net.minecraft.world.item.ItemStack> icon);
    *///?}

    /** A block entity type for one block; called from inside a registration factory. */
    <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityType<T> blockEntityType(
            java.util.function.BiFunction<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState, T> factory,
            net.minecraft.world.level.block.Block block);

    /** A menu type whose client side is built from data the server writes when opening it. */
    <M extends AbstractContainerMenu> Supplier<MenuType<M>> registerMenu(String name, MenuFactory<M> factory);

    void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> openData);

    /** Registers a server-to-client packet. The handler runs on the client's main thread. */
    <P> void registerClientbound(String name, Class<P> type, BiConsumer<P, FriendlyByteBuf> writer,
                                 Function<FriendlyByteBuf, P> reader, Consumer<P> handler);

    /** Registers a client-to-server packet. The handler runs on the server's main thread. */
    <P> void registerServerbound(String name, Class<P> type, BiConsumer<P, FriendlyByteBuf> writer,
                                 Function<FriendlyByteBuf, P> reader, BiConsumer<P, ServerPlayer> handler);

    void sendToPlayer(ServerPlayer player, Object payload);

    void sendToServer(Object payload);

    @FunctionalInterface
    interface MenuFactory<M extends AbstractContainerMenu> {
        M create(int containerId, Inventory inventory, FriendlyByteBuf data);
    }
}
