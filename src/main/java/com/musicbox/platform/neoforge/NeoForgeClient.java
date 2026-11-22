package com.musicbox.platform.neoforge;

import com.musicbox.client.ClientSetup;
import com.musicbox.client.MusicBoxScreen;
import com.musicbox.client.SpeakerScreen;
import com.musicbox.client.render.MusicBoxRenderer;
import com.musicbox.client.render.SpeakerRenderer;
import com.musicbox.registry.ModBlockEntities;
import com.musicbox.registry.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

final class NeoForgeClient {

    private NeoForgeClient() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class, event -> ClientSetup.init());
        modBus.addListener(RegisterMenuScreensEvent.class, event -> {
            event.register(ModMenus.MUSIC_BOX.get(), MusicBoxScreen::new);
            event.register(ModMenus.SPEAKER.get(), SpeakerScreen::new);
        });
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerBlockEntityRenderer(ModBlockEntities.MUSIC_BOX.get(), MusicBoxRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.SPEAKER.get(), SpeakerRenderer::new);
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> ClientSetup.tick());
    }
}
