package com.musicbox.platform.forge;

import com.musicbox.MusicBox;
import com.musicbox.platform.Platform;
import com.musicbox.platform.RegistryRef;
import com.musicbox.registry.Keys;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ForgePlatform implements Platform {

    private final Map<ResourceKey<?>, DeferredRegister<?>> registers = new LinkedHashMap<>();
    private int nextPacketId;

    //? if >=1.20.2 {
    private static final net.minecraftforge.network.SimpleChannel CHANNEL = net.minecraftforge.network.ChannelBuilder
            .named(MusicBox.id("main"))
            .networkProtocolVersion(1)
            .simpleChannel();
    //?} else {
    /*private static final String PROTOCOL = "1";
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL =
            net.minecraftforge.network.NetworkRegistry.ChannelBuilder
                    .named(MusicBox.id("main"))
                    .networkProtocolVersion(() -> PROTOCOL)
                    .clientAcceptedVersions(PROTOCOL::equals)
                    .serverAcceptedVersions(PROTOCOL::equals)
                    .simpleChannel();
    *///?}

    /** Hands the queued registrations to the mod bus; called once from the entry point. */
    void attach(IEventBus modBus) {
        registers.values().forEach(register -> register.register(modBus));
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
        RegistryObject<T> object = register.register(name, factory);
        return new RegistryRef<>() {
            @Override
            public T get() {
                return object.get();
            }

            @Override
            public Holder<T> holder() {
                //? if >=1.20.5 {
                // Forge's RegistryObject cannot hand out a holder for a registry it does not
                // wrap (armor materials, for one). The registry's own registration lookup can,
                // and it resolves to the same entry once registration is done.
                ResourceKey<T> key = ResourceKey.create((ResourceKey) registry, MusicBox.id(name));
                net.minecraft.core.WritableRegistry<T> writable = (net.minecraft.core.WritableRegistry<T>)
                        //? if >=1.21.2 {
                        net.minecraft.core.registries.BuiltInRegistries.REGISTRY.getValue(registry.location());
                        //?} else {
                        /*net.minecraft.core.registries.BuiltInRegistries.REGISTRY.get(registry.location());
                        *///?}
                return writable.createRegistrationLookup().getOrThrow(key);
                //?} else {
                /*return object.getHolder().orElseThrow();
                *///?}
            }
        };
    }

    //? if >=1.20 {
    @Override
    public CreativeModeTab.Builder tabBuilder() {
        return CreativeModeTab.builder();
    }
    //?} else {
    /*@Override
    public CreativeModeTab createTab(String name, Supplier<net.minecraft.world.item.ItemStack> icon) {
        return new CreativeModeTab(MusicBox.MOD_ID + "." + name) {
            @Override
            public net.minecraft.world.item.ItemStack makeIcon() {
                return icon.get();
            }
        };
    }
    *///?}

    @Override
    public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityType<T> blockEntityType(
            java.util.function.BiFunction<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState, T> factory,
            net.minecraft.world.level.block.Block block) {
        //? if >=1.21.2 {
        return new net.minecraft.world.level.block.entity.BlockEntityType<>(factory::apply, java.util.Set.of(block));
        //?} else {
        /*return net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(factory::apply, block).build(null);
        *///?}
    }
    @Override
    public <M extends AbstractContainerMenu> Supplier<MenuType<M>> registerMenu(String name, MenuFactory<M> factory) {
        return register(Keys.MENU, name, () -> IForgeMenuType.create(factory::create));
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> openData) {
        //? if >=1.20.2 {
        player.openMenu(provider, openData);
        //?} else {
        /*net.minecraftforge.network.NetworkHooks.openScreen(player, provider, openData);
        *///?}
    }

    @Override
    public <P> void registerClientbound(String name, Class<P> type, BiConsumer<P, FriendlyByteBuf> writer,
                                        Function<FriendlyByteBuf, P> reader, Consumer<P> handler) {
        //? if >=1.20.2 {
        CHANNEL.messageBuilder(type, nextPacketId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
                .encoder((payload, buf) -> writer.accept(payload, buf))
                .decoder(buf -> reader.apply(buf))
                .consumerMainThread((payload, context) -> handler.accept(payload))
                .add();
        //?} else {
        /*CHANNEL.registerMessage(nextPacketId++, type, writer, reader, (payload, context) -> {
            context.get().enqueueWork(() -> handler.accept(payload));
            context.get().setPacketHandled(true);
        });
        *///?}
    }

    @Override
    public <P> void registerServerbound(String name, Class<P> type, BiConsumer<P, FriendlyByteBuf> writer,
                                        Function<FriendlyByteBuf, P> reader, BiConsumer<P, ServerPlayer> handler) {
        //? if >=1.20.2 {
        CHANNEL.messageBuilder(type, nextPacketId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder((payload, buf) -> writer.accept(payload, buf))
                .decoder(buf -> reader.apply(buf))
                .consumerMainThread((payload, context) -> {
                    ServerPlayer sender = context.getSender();
                    if (sender != null) {
                        handler.accept(payload, sender);
                    }
                })
                .add();
        //?} else {
        /*CHANNEL.registerMessage(nextPacketId++, type, writer, reader, (payload, context) -> {
            net.minecraftforge.network.NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> {
                ServerPlayer sender = ctx.getSender();
                if (sender != null) {
                    handler.accept(payload, sender);
                }
            });
            ctx.setPacketHandled(true);
        });
        *///?}
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Object payload) {
        //? if >=1.20.2 {
        CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
        //?} else {
        /*CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
        *///?}
    }

    @Override
    public void sendToServer(Object payload) {
        //? if >=1.20.2 {
        CHANNEL.send(payload, PacketDistributor.SERVER.noArg());
        //?} else {
        /*CHANNEL.sendToServer(payload);
        *///?}
    }
}
