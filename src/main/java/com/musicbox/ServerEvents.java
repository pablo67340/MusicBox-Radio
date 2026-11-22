package com.musicbox;

import com.musicbox.network.HeadphoneSync;
import com.musicbox.network.ModNetwork;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/** Server lifecycle hooks; each loader forwards its own events here. */
public final class ServerEvents {

    private static int counter;

    private ServerEvents() {
    }

    /** Called at the end of every server tick. */
    public static void onServerTick(MinecraftServer server) {
        if (++counter < HeadphoneSync.TICK_INTERVAL) {
            return;
        }
        counter = 0;
        HeadphoneSync.tick(server, ModNetwork::sendPairedBox);
    }

    public static void onLogin(UUID player) {
        HeadphoneSync.forget(player);
    }

    public static void onServerStopped() {
        HeadphoneSync.clear();
    }
}
