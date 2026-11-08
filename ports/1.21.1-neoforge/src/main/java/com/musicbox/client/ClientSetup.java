package com.musicbox.client;

import com.musicbox.ClientHooks;
import com.musicbox.MusicBox;
import com.musicbox.client.audio.RadioManager;
import com.musicbox.client.render.MusicBoxRenderer;
import com.musicbox.client.render.SpeakerRenderer;
import com.musicbox.registry.ModBlockEntities;
import com.musicbox.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = MusicBox.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ClientConfig.load(FMLPaths.CONFIGDIR.get().resolve(MusicBox.MOD_ID));
        ClientHooks.setListener(RadioManager.get());
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.MUSIC_BOX.get(), MusicBoxScreen::new);
        event.register(ModMenus.SPEAKER.get(), SpeakerScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.MUSIC_BOX.get(), MusicBoxRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SPEAKER.get(), SpeakerRenderer::new);
    }

    @EventBusSubscriber(modid = MusicBox.MOD_ID, value = Dist.CLIENT)
    public static final class Playback {

        private Playback() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            RadioManager.get().tick();
        }
    }
}
