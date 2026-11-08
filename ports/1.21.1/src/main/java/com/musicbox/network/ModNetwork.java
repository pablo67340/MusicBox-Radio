package com.musicbox.network;

import com.musicbox.MusicBox;
import com.musicbox.blockentity.MusicBoxBlockEntity;
import com.musicbox.menu.MusicBoxMenu;
import com.musicbox.registry.ModMenus;
import com.musicbox.station.StationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

public final class ModNetwork {

    private static final int VERSION = 1;

    private static final SimpleChannel CHANNEL = ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(MusicBox.MOD_ID, "main"))
            .networkProtocolVersion(VERSION)
            .simpleChannel();

    private ModNetwork() {
    }

    public static void init() {
        CHANNEL.messageBuilder(PairedBoxPayload.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PairedBoxPayload::write)
                .decoder(PairedBoxPayload::read)
                .consumerMainThread((payload, ctx) -> DistExecutor.unsafeRunWhenOn(
                        Dist.CLIENT, () -> () -> receive(payload)))
                .add();

        CHANNEL.messageBuilder(AddStationPayload.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AddStationPayload::write)
                .decoder(AddStationPayload::read)
                .consumerMainThread((payload, ctx) -> {
                    ServerPlayer sender = ctx.getSender();
                    if (sender != null && payload.apply(sender)) {
                        reopen(sender, payload.pos());
                    }
                })
                .add();
    }

    public static void sendPairedBox(ServerPlayer player, PairedBoxPayload payload) {
        CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
    }

    public static void sendAddStation(AddStationPayload payload) {
        CHANNEL.send(payload, PacketDistributor.SERVER.noArg());
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
