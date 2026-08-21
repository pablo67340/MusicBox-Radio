package com.musicbox.client;

import com.musicbox.network.AddStationPayload;
import com.musicbox.network.ModNetwork;

/**
 * The client half of the mod's channel, kept behind one loader-neutral name so the screens
 * can call it identically on Forge and Fabric.
 */
public final class ClientNetwork {

    private ClientNetwork() {
    }

    public static void sendAddStation(AddStationPayload payload) {
        ModNetwork.sendAddStation(payload);
    }
}
