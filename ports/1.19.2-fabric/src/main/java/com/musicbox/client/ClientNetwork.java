package com.musicbox.client;

import com.musicbox.network.AddStationPayload;
import com.musicbox.network.ModNetwork;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

/**
 * The client half of the mod's channel, kept behind one loader-neutral name so the screens
 * can call it identically on Forge and Fabric.
 */
@Environment(EnvType.CLIENT)
public final class ClientNetwork {

    private ClientNetwork() {
    }

    public static void sendAddStation(AddStationPayload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        payload.write(buf);
        ClientPlayNetworking.send(ModNetwork.ADD_STATION, buf);
    }
}
