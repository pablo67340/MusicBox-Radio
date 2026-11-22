package com.musicbox.network;

import com.musicbox.blockentity.MusicBoxBlockEntity;
import com.musicbox.menu.MusicBoxMenu;
import com.musicbox.platform.Services;
import com.musicbox.station.StationConfig;
import com.musicbox.util.Levels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public final class ModNetwork {

    private ModNetwork() {
    }

    public static void init() {
        // The handler is a lambda rather than a method reference so the client-only class
        // behind it is only resolved when a packet actually arrives on a client.
        Services.PLATFORM.registerClientbound("paired_box", PairedBoxPayload.class,
                PairedBoxPayload::write, PairedBoxPayload::read,
                payload -> com.musicbox.client.ClientSetup.receivePairedBox(payload));

        Services.PLATFORM.registerServerbound("add_station", AddStationPayload.class,
                AddStationPayload::write, AddStationPayload::read,
                (payload, sender) -> {
                    if (payload.apply(sender)) {
                        reopen(sender, payload.pos());
                    }
                });
    }

    public static void sendPairedBox(ServerPlayer player, PairedBoxPayload payload) {
        Services.PLATFORM.sendToPlayer(player, payload);
    }

    /** Pushes the menu again so the sender's list includes what they just added. */
    private static void reopen(ServerPlayer player, BlockPos pos) {
        if (Levels.of(player).getBlockEntity(pos) instanceof MusicBoxBlockEntity box) {
            openMusicBox(player, box, pos);
        }
    }

    public static void openMusicBox(ServerPlayer player, MusicBoxBlockEntity box, BlockPos pos) {
        Services.PLATFORM.openMenu(player, box, buf -> MusicBoxMenu.writeOpenData(buf, pos,
                StationConfig.combined(box.getCustomStations()),
                StationConfig.mayAddStations(player),
                StationConfig.scope() == StationConfig.Scope.GLOBAL));
    }
}
