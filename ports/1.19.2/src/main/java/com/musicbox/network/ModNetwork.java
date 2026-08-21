package com.musicbox.network;

import com.musicbox.MusicBox;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
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
    }

    public static void sendPairedBox(ServerPlayer player, PairedBoxPayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    // Split out so the client-only class is not resolved on a dedicated server.
    private static void receive(PairedBoxPayload payload) {
        com.musicbox.client.audio.PairedFeed.accept(payload);
    }
}
