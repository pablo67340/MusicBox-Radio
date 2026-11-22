package com.musicbox.platform.fabric;

import com.musicbox.MusicBox;
import com.musicbox.platform.Platform;
import com.musicbox.platform.RegistryRef;
import com.musicbox.registry.Keys;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class FabricPlatform implements Platform {

    /** Clientbound channels, registered by {@link FabricClient} since the receiver is client only. */
    final List<Channel<?>> clientbound = new ArrayList<>();
    private final Map<Class<?>, Channel<?>> channelsByType = new HashMap<>();

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> RegistryRef<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<T> factory) {
        //? if >=1.19.3 {
        Registry<T> target = (Registry<T>) net.minecraft.core.registries.BuiltInRegistries.REGISTRY.get(registry.location());
        //?} else {
        /*Registry<T> target = (Registry<T>) Registry.REGISTRY.get(registry.location());
        *///?}
        ResourceLocation id = MusicBox.id(name);
        T value = Registry.register(target, id, factory.get());
        ResourceKey<T> key = ResourceKey.create((ResourceKey<? extends Registry<T>>) registry, id);
        return new RegistryRef<>() {
            @Override
            public T get() {
                return value;
            }

            @Override
            public Holder<T> holder() {
                return target.getHolderOrThrow(key);
            }
        };
    }

    //? if >=1.20 {
    @Override
    public CreativeModeTab.Builder tabBuilder() {
        return net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder();
    }
    //?} else {
    /*@Override
    public CreativeModeTab createTab(String name, Supplier<net.minecraft.world.item.ItemStack> icon) {
        return net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder.create(MusicBox.id(name)).icon(icon).build();
    }
    *///?}

    @Override
    public <M extends AbstractContainerMenu> Supplier<MenuType<M>> registerMenu(String name, MenuFactory<M> factory) {
        return register(Keys.MENU, name, () -> new ExtendedScreenHandlerType<>(factory::create));
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> openData) {
        player.openMenu(new ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayer target, FriendlyByteBuf buf) {
                openData.accept(buf);
            }

            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
                return provider.createMenu(containerId, inventory, opener);
            }
        });
    }

    @Override
    public <P> void registerClientbound(String name, Class<P> type, BiConsumer<P, FriendlyByteBuf> writer,
                                        Function<FriendlyByteBuf, P> reader, Consumer<P> handler) {
        Channel<P> channel = new Channel<>(MusicBox.id(name), writer, reader);
        channel.clientHandler = handler;
        clientbound.add(channel);
        channelsByType.put(type, channel);
    }

    @Override
    public <P> void registerServerbound(String name, Class<P> type, BiConsumer<P, FriendlyByteBuf> writer,
                                        Function<FriendlyByteBuf, P> reader, BiConsumer<P, ServerPlayer> handler) {
        Channel<P> channel = new Channel<>(MusicBox.id(name), writer, reader);
        channelsByType.put(type, channel);
        ServerPlayNetworking.registerGlobalReceiver(channel.id, (server, player, listener, buf, sender) -> {
            P payload = reader.apply(buf);
            server.execute(() -> handler.accept(payload, player));
        });
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Object payload) {
        Channel<?> channel = channel(payload);
        ServerPlayNetworking.send(player, channel.id, channel.encode(payload));
    }

    @Override
    public void sendToServer(Object payload) {
        Channel<?> channel = channel(payload);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(channel.id, channel.encode(payload));
    }

    private Channel<?> channel(Object payload) {
        Channel<?> channel = channelsByType.get(payload.getClass());
        if (channel == null) {
            throw new IllegalArgumentException("Unregistered packet " + payload.getClass().getName());
        }
        return channel;
    }

    static final class Channel<P> {

        final ResourceLocation id;
        final BiConsumer<P, FriendlyByteBuf> writer;
        final Function<FriendlyByteBuf, P> reader;
        Consumer<P> clientHandler;

        Channel(ResourceLocation id, BiConsumer<P, FriendlyByteBuf> writer, Function<FriendlyByteBuf, P> reader) {
            this.id = id;
            this.writer = writer;
            this.reader = reader;
        }

        @SuppressWarnings("unchecked")
        FriendlyByteBuf encode(Object payload) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            writer.accept((P) payload, buf);
            return buf;
        }
    }
}
