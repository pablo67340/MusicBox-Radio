package com.musicbox.client;

import com.musicbox.ClientHooks;
import com.musicbox.MusicBox;
import com.musicbox.client.audio.PairedFeed;
import com.musicbox.client.audio.RadioManager;
import com.musicbox.client.render.MusicBoxRenderer;
import com.musicbox.client.render.SpeakerRenderer;
import com.musicbox.network.ModNetwork;
import com.musicbox.network.PairedBoxPayload;
import com.musicbox.registry.ModBlockEntities;
import com.musicbox.registry.ModBlocks;
import com.musicbox.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;

public final class MusicBoxClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientConfig.load(FabricLoader.getInstance().getConfigDir().resolve(MusicBox.MOD_ID));
        ClientHooks.setListener(RadioManager.get());

        MenuScreens.register(ModMenus.MUSIC_BOX, MusicBoxScreen::new);
        MenuScreens.register(ModMenus.SPEAKER, SpeakerScreen::new);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.MUSIC_BOX, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.SPEAKER, RenderType.cutout());

        BlockEntityRendererRegistry.register(ModBlockEntities.MUSIC_BOX, MusicBoxRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.SPEAKER, SpeakerRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.PAIRED_BOX,
                (client, handler, buf, responder) -> {
                    PairedBoxPayload payload = PairedBoxPayload.read(buf);
                    client.execute(() -> PairedFeed.accept(payload));
                });

        ClientTickEvents.END_CLIENT_TICK.register(client -> RadioManager.get().tick());
    }
}
