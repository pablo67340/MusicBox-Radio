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

@Mod(MusicBox.MOD_ID)
public final class NeoForgeMod {

    public NeoForgeMod(IEventBus modBus) {
        MusicBox.init();
        ((NeoForgePlatform) Services.PLATFORM).attach(modBus);

        //? if >=1.20.5 {
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class,
                event -> ServerEvents.onServerTick(event.getServer()));
        //?} else {
        /*NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.TickEvent.ServerTickEvent.class, event -> {
            if (event.phase == net.neoforged.neoforge.event.TickEvent.Phase.END) {
                ServerEvents.onServerTick(net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer());
            }
        });
        *///?}
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class,
                event -> ServerEvents.onLogin(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> ServerEvents.onServerStopped());

        if (FMLEnvironment.dist.isClient()) {
            NeoForgeClient.init(modBus);
        }
    }
}
