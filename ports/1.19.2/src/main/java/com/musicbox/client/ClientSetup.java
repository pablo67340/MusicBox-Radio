package com.musicbox.client;

import com.musicbox.ClientHooks;
import com.musicbox.MusicBox;
import com.musicbox.client.audio.RadioManager;
import com.musicbox.client.render.MusicBoxRenderer;
import com.musicbox.client.render.SpeakerRenderer;
import com.musicbox.registry.ModBlockEntities;
import com.musicbox.registry.ModBlocks;
import com.musicbox.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod.EventBusSubscriber(modid = MusicBox.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ClientConfig.load(FMLPaths.CONFIGDIR.get().resolve(MusicBox.MOD_ID));
        ClientHooks.setListener(RadioManager.get());

        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.MUSIC_BOX.get(), MusicBoxScreen::new);
            MenuScreens.register(ModMenus.SPEAKER.get(), SpeakerScreen::new);
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.MUSIC_BOX.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.SPEAKER.get(), RenderType.cutout());
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.MUSIC_BOX.get(), MusicBoxRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SPEAKER.get(), SpeakerRenderer::new);
    }

    @Mod.EventBusSubscriber(modid = MusicBox.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Playback {

        private Playback() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                RadioManager.get().tick();
            }
        }
    }
}
