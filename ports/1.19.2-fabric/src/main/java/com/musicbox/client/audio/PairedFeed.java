package com.musicbox.client.audio;

import com.musicbox.network.PairedBoxPayload;

/**
 * The server's last word on what this client's paired music box is doing. Written from the
 * network thread and read on the client tick, hence the volatile.
 */
public final class PairedFeed {

    private static volatile PairedBoxPayload current = PairedBoxPayload.NONE;

    private PairedFeed() {
    }

    public static void accept(PairedBoxPayload payload) {
        current = payload;
    }

    public static PairedBoxPayload get() {
        return current;
    }

    public static void reset() {
        current = PairedBoxPayload.NONE;
    }
}
