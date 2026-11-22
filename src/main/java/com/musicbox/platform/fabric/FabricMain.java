package com.musicbox.platform.fabric;

import com.musicbox.MusicBox;
import com.musicbox.ServerEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class FabricMain implements ModInitializer {

    @Override
    public void onInitialize() {
        MusicBox.init();
        ServerTickEvents.END_SERVER_TICK.register(ServerEvents::onServerTick);
        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) ->
                ServerEvents.onLogin(listener.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerEvents.onServerStopped());
    }
}
