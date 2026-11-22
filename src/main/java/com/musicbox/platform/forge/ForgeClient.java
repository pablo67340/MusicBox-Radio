package com.musicbox.platform.forge;

import com.musicbox.client.ClientSetup;
import com.musicbox.client.MusicBoxScreen;
import com.musicbox.client.SpeakerScreen;
import com.musicbox.client.render.MusicBoxRenderer;
import com.musicbox.client.render.SpeakerRenderer;
import com.musicbox.registry.ModBlockEntities;
import com.musicbox.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

final class ForgeClient {

    private ForgeClient() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener(EventPriority.NORMAL, false, FMLClientSetupEvent.class, event -> {
            ClientSetup.init();
            event.enqueueWork(() -> {
                MenuScreens.register(ModMenus.MUSIC_BOX.get(), MusicBoxScreen::new);
                MenuScreens.register(ModMenus.SPEAKER.get(), SpeakerScreen::new);
            });
        });
        modBus.addListener(EventPriority.NORMAL, false, EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerBlockEntityRenderer(ModBlockEntities.MUSIC_BOX.get(), MusicBoxRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.SPEAKER.get(), SpeakerRenderer::new);
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.class, event -> {
            if (event.phase == TickEvent.Phase.END) {
                ClientSetup.tick();
            }
        });
    }
}
