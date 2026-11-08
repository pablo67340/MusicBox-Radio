package com.musicbox;

import com.musicbox.network.HeadphoneSync;
import com.musicbox.network.ModNetwork;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = MusicBox.MOD_ID)
public final class ServerEvents {

    private static int counter;

    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++counter < HeadphoneSync.TICK_INTERVAL) {
            return;
        }
        counter = 0;
        HeadphoneSync.tick(event.getServer(), ModNetwork::sendPairedBox);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        HeadphoneSync.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        HeadphoneSync.clear();
    }
}
