package com.musicbox;

import com.musicbox.network.HeadphoneSync;
import com.musicbox.network.ModNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = MusicBox.MOD_ID)
public final class ServerEvents {

    private static int counter;

    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++counter < HeadphoneSync.TICK_INTERVAL) {
            return;
        }
        counter = 0;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            HeadphoneSync.tick(server, ModNetwork::sendPairedBox);
        }
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
