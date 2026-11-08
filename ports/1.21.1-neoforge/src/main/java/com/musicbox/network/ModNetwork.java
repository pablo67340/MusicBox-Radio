package com.musicbox.network;

import com.musicbox.blockentity.MusicBoxBlockEntity;
import com.musicbox.menu.MusicBoxMenu;
import com.musicbox.registry.ModMenus;
import com.musicbox.station.StationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {

    private static final String VERSION = "1";

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);

        registrar.playToClient(PairedBoxPayload.TYPE, PairedBoxPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> receive(payload)));

        registrar.playToServer(AddStationPayload.TYPE, AddStationPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sender && payload.apply(sender)) {
                        reopen(sender, payload.pos());
                    }
                }));
    }

    public static void sendPairedBox(ServerPlayer player, PairedBoxPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendAddStation(AddStationPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /** Pushes the menu again so the sender's list includes what they just added. */
    private static void reopen(ServerPlayer player, BlockPos pos) {
        if (player.level().getBlockEntity(pos) instanceof MusicBoxBlockEntity box) {
            ModMenus.open(player, box, buf -> MusicBoxMenu.writeOpenData(buf, pos,
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
