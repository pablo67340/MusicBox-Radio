package com.musicbox.platform.forge;

import com.musicbox.MusicBox;
import com.musicbox.ServerEvents;
import com.musicbox.compat.CuriosCompat;
import com.musicbox.platform.Services;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;

@Mod(MusicBox.MOD_ID)
public final class ForgeMod {

    //? if >=1.21 {
    public ForgeMod(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
    //?} else {
    /*public ForgeMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
    *///?}
        MusicBox.init();
        ((ForgePlatform) Services.PLATFORM).attach(modBus);
        modBus.addListener(EventPriority.NORMAL, false, InterModEnqueueEvent.class, event -> requestCuriosSlot());

        IEventBus forgeBus = MinecraftForge.EVENT_BUS;
        forgeBus.addListener(EventPriority.NORMAL, false, TickEvent.ServerTickEvent.class, event -> {
            if (event.phase == TickEvent.Phase.END) {
                ServerEvents.onServerTick(ServerLifecycleHooks.getCurrentServer());
            }
        });
        forgeBus.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerLoggedInEvent.class,
                event -> ServerEvents.onLogin(event.getEntity().getUUID()));
        forgeBus.addListener(EventPriority.NORMAL, false, ServerStoppedEvent.class,
                event -> ServerEvents.onServerStopped());

        if (FMLEnvironment.dist.isClient()) {
            ForgeClient.init(modBus);
        }
    }

    private static void requestCuriosSlot() {
        if (!Services.PLATFORM.isModLoaded("curios")) {
            return;
        }
        Map.Entry<String, Object> request = CuriosCompat.headSlotRequest();
        if (request != null) {
            InterModComms.sendTo("curios", request.getKey(), request::getValue);
        }
    }
}
