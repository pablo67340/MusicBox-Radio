package com.musicbox.platform.fabric;

import com.musicbox.client.ClientSetup;
import com.musicbox.client.MusicBoxScreen;
import com.musicbox.client.SpeakerScreen;
import com.musicbox.client.render.MusicBoxRenderer;
import com.musicbox.client.render.SpeakerRenderer;
import com.musicbox.platform.Services;
import com.musicbox.registry.ModBlockEntities;
import com.musicbox.registry.ModBlocks;
import com.musicbox.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;

public final class FabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientSetup.init();
        MenuScreens.register(ModMenus.MUSIC_BOX.get(), MusicBoxScreen::new);
        MenuScreens.register(ModMenus.SPEAKER.get(), SpeakerScreen::new);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.MUSIC_BOX.get(), RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.SPEAKER.get(), RenderType.cutout());
        BlockEntityRendererRegistry.register(ModBlockEntities.MUSIC_BOX.get(), MusicBoxRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.SPEAKER.get(), SpeakerRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientSetup.tick());

        ((FabricPlatform) Services.PLATFORM).clientbound.forEach(FabricClient::listen);
    }

    private static <P> void listen(FabricPlatform.Channel<P> channel) {
        //? if >=1.20.5 {
        ClientPlayNetworking.registerGlobalReceiver(channel.type,
                (packet, context) -> channel.clientHandler.accept(packet.payload()));
        //?} else {
        /*ClientPlayNetworking.registerGlobalReceiver(channel.id, (client, listener, buf, responder) -> {
            P payload = channel.reader.apply(buf);
            client.execute(() -> channel.clientHandler.accept(payload));
        });
        *///?}
    }
}
