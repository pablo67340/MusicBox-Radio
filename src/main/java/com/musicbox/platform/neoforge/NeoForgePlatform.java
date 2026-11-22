package com.musicbox.platform.neoforge;

import com.musicbox.MusicBox;
import com.musicbox.platform.Platform;
import com.musicbox.platform.RegistryRef;
import com.musicbox.registry.Keys;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class NeoForgePlatform implements Platform {

    private static final String PROTOCOL = "1";

    private final Map<ResourceKey<?>, DeferredRegister<?>> registers = new LinkedHashMap<>();
    private final List<Channel<?>> channels = new ArrayList<>();
    private final Map<Class<?>, Channel<?>> channelsByType = new HashMap<>();

    /** Hands the queued registrations and packets to the mod bus; called once from the entry point. */
    void attach(IEventBus modBus) {
        registers.values().forEach(register -> register.register(modBus));
        modBus.addListener(RegisterPayloadHandlersEvent.class, this::registerPayloads);
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> RegistryRef<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<T> factory) {
        DeferredRegister<Object> register = (DeferredRegister<Object>) registers.computeIfAbsent(registry,
                key -> DeferredRegister.create((ResourceKey) key, MusicBox.MOD_ID));
        DeferredHolder<Object, T> holder = register.register(name, factory);
        return new RegistryRef<>() {
            @Override
            public T get() {
                return holder.get();
            }

            @Override
            public Holder<T> holder() {
                return (Holder<T>) (Holder) holder;
            }
        };
    }

    @Override
    public CreativeModeTab.Builder tabBuilder() {
        return CreativeModeTab.builder();
    }

    @Override
    public <M extends AbstractContainerMenu> Supplier<MenuType<M>> registerMenu(String name, MenuFactory<M> factory) {
        return register(Keys.MENU, name, () -> IMenuTypeExtension.create(factory::create));
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> openData) {
        player.openMenu(provider, openData::accept);
    }

    @Override
    public <P> void registerClientbound(String name, Class<P> type, BiConsumer<P, FriendlyByteBuf> writer,
                                        Function<FriendlyByteBuf, P> reader, Consumer<P> handler) {
        add(new Channel<>(name, writer, reader, handler, null), type);
    }

    @Override
    public <P> void registerServerbound(String name, Class<P> type, BiConsumer<P, FriendlyByteBuf> writer,
                                        Function<FriendlyByteBuf, P> reader, BiConsumer<P, ServerPlayer> handler) {
        add(new Channel<>(name, writer, reader, null, handler), type);
    }

    private void add(Channel<?> channel, Class<?> type) {
        channels.add(channel);
        channelsByType.put(type, channel);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Object payload) {
        PacketDistributor.sendToPlayer(player, wrap(payload));
    }

    @Override
    public void sendToServer(Object payload) {
        PacketDistributor.sendToServer(wrap(payload));
    }

    @SuppressWarnings("unchecked")
    private <P> Packet<P> wrap(P payload) {
        Channel<P> channel = (Channel<P>) channelsByType.get(payload.getClass());
        if (channel == null) {
            throw new IllegalArgumentException("Unregistered packet " + payload.getClass().getName());
        }
        return new Packet<>(channel, payload);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        channels.forEach(channel -> channel.register(registrar));
    }

    /** One packet type; the payload itself stays a plain record with its own read and write. */
    private static final class Channel<P> {

        final CustomPacketPayload.Type<Packet<P>> type;
        final StreamCodec<FriendlyByteBuf, Packet<P>> codec;
        final Consumer<P> clientHandler;
        final BiConsumer<P, ServerPlayer> serverHandler;

        Channel(String name, BiConsumer<P, FriendlyByteBuf> writer, Function<FriendlyByteBuf, P> reader,
                Consumer<P> clientHandler, BiConsumer<P, ServerPlayer> serverHandler) {
            this.type = new CustomPacketPayload.Type<>(MusicBox.id(name));
            this.codec = StreamCodec.of((buf, packet) -> writer.accept(packet.payload(), buf),
                    buf -> new Packet<>(this, reader.apply(buf)));
            this.clientHandler = clientHandler;
            this.serverHandler = serverHandler;
        }

        void register(PayloadRegistrar registrar) {
            if (clientHandler != null) {
                registrar.playToClient(type, codec,
                        (packet, context) -> context.enqueueWork(() -> clientHandler.accept(packet.payload())));
            } else {
                registrar.playToServer(type, codec, (packet, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sender) {
                        serverHandler.accept(packet.payload(), sender);
                    }
                }));
            }
        }
    }

    private record Packet<P>(Channel<P> channel, P payload) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return channel.type;
        }
    }
}
