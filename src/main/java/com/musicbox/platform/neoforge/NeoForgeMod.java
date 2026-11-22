package com.musicbox.platform.neoforge;

import com.musicbox.MusicBox;
import com.musicbox.ServerEvents;
import com.musicbox.platform.Services;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(MusicBox.MOD_ID)
public final class NeoForgeMod {

    public NeoForgeMod(IEventBus modBus) {
        MusicBox.init();
        ((NeoForgePlatform) Services.PLATFORM).attach(modBus);

        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
                event -> ServerEvents.onServerTick(event.getServer()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class,
                event -> ServerEvents.onLogin(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> ServerEvents.onServerStopped());

        if (FMLEnvironment.dist.isClient()) {
            NeoForgeClient.init(modBus);
        }
    }
}
