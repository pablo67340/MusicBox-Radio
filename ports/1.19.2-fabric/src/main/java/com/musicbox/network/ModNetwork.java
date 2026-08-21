package com.musicbox.network;

import com.musicbox.MusicBox;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class ModNetwork {

    public static final ResourceLocation PAIRED_BOX = new ResourceLocation(MusicBox.MOD_ID, "paired_box");

    private ModNetwork() {
    }

    public static void sendPairedBox(ServerPlayer player, PairedBoxPayload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        payload.write(buf);
        ServerPlayNetworking.send(player, PAIRED_BOX, buf);
    }
}
