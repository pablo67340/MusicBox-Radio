package com.musicbox.network;

import com.musicbox.MusicBox;
import com.musicbox.blockentity.MusicBoxBlockEntity;
import com.musicbox.menu.MusicBoxMenu;
import com.musicbox.station.StationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {

    private static final String VERSION = "1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(MusicBox.MOD_ID, "main"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(VERSION::equals)
            .serverAcceptedVersions(VERSION::equals)
            .simpleChannel();

    private ModNetwork() {
    }

    public static void init() {
        CHANNEL.registerMessage(0, PairedBoxPayload.class,
                (payload, buf) -> payload.write(buf),
                PairedBoxPayload::read,
                (payload, ctx) -> {
                    ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                            Dist.CLIENT, () -> () -> receive(payload)));
                    ctx.get().setPacketHandled(true);
                });

        CHANNEL.registerMessage(1, AddStationPayload.class,
                (payload, buf) -> payload.write(buf),
                AddStationPayload::read,
                (payload, ctx) -> {
                    NetworkEvent.Context context = ctx.get();
                    context.enqueueWork(() -> {
                        ServerPlayer sender = context.getSender();
                        if (sender != null && payload.apply(sender)) {
                            reopen(sender, payload.pos());
                        }
                    });
                    context.setPacketHandled(true);
                });
    }

    public static void sendPairedBox(ServerPlayer player, PairedBoxPayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendAddStation(AddStationPayload payload) {
        CHANNEL.sendToServer(payload);
    }

    /** Pushes the menu again so the sender's list includes what they just added. */
    private static void reopen(ServerPlayer player, BlockPos pos) {
        if (player.getLevel().getBlockEntity(pos) instanceof MusicBoxBlockEntity box) {
            NetworkHooks.openScreen(player, box, buf -> MusicBoxMenu.writeOpenData(buf, pos,
                    StationConfig.combined(box.getCustomStations()),
                    StationConfig.mayAddStations(player),
                    StationConfig.scope() == StationConfig.Scope.GLOBAL));
        }
    }

    // Split out so the client-only class is not resolved on a dedicated server.
    private static void receive(PairedBoxPayload payload) {
        com.musicbox.client.audio.PairedFeed.accept(payload);
    }
}
