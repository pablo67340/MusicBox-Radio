package com.musicbox.network;

import com.musicbox.MusicBox;
import com.musicbox.blockentity.MusicBoxBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class ModNetwork {

    public static final ResourceLocation PAIRED_BOX = new ResourceLocation(MusicBox.MOD_ID, "paired_box");
    public static final ResourceLocation ADD_STATION = new ResourceLocation(MusicBox.MOD_ID, "add_station");

    private ModNetwork() {
    }

    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(ADD_STATION, (server, player, handler, buf, sender) -> {
            AddStationPayload payload = AddStationPayload.read(buf);
            server.execute(() -> {
                if (payload.apply(player)) {
                    reopen(player, payload);
                }
            });
        });
    }

    public static void sendPairedBox(ServerPlayer player, PairedBoxPayload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        payload.write(buf);
        ServerPlayNetworking.send(player, PAIRED_BOX, buf);
    }

    /** Pushes the menu again so the sender's list includes what they just added. */
    private static void reopen(ServerPlayer player, AddStationPayload payload) {
        if (player.getLevel().getBlockEntity(payload.pos()) instanceof MusicBoxBlockEntity box) {
            player.openMenu(box);
        }
    }
}
