package com.musicbox.client;

import com.musicbox.network.AddStationPayload;
import com.musicbox.platform.Services;

/** The client half of the mod's channel. */
public final class ClientNetwork {

    private ClientNetwork() {
    }

    public static void sendAddStation(AddStationPayload payload) {
        Services.PLATFORM.sendToServer(payload);
    }
}
