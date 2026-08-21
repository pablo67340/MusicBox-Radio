package com.musicbox;

import com.musicbox.network.HeadphoneSync;
import com.musicbox.network.ModNetwork;
import com.musicbox.registry.ModBlockEntities;
import com.musicbox.registry.ModBlocks;
import com.musicbox.registry.ModItems;
import com.musicbox.registry.ModMenus;
import com.musicbox.station.StationConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MusicBox implements ModInitializer {

    public static final String MOD_ID = "musicboxradio";
    public static final Logger LOGGER = LoggerFactory.getLogger("Music Box");

    /** Station indices travel as button ids in a vanilla packet, which bounds the list size. */
    public static final int MAX_STATIONS = 100;

    public static final CreativeModeTab TAB = FabricItemGroupBuilder
            .create(new ResourceLocation(MOD_ID, "general"))
            .icon(() -> new ItemStack(ModItems.MUSIC_BOX))
            .build();

    private static int tickCounter;

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        StationConfig.load(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID));

        ModBlocks.register();
        ModItems.register();
        ModBlockEntities.register();
        ModMenus.register();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter >= HeadphoneSync.TICK_INTERVAL) {
                tickCounter = 0;
                HeadphoneSync.tick(server, ModNetwork::sendPairedBox);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> HeadphoneSync.clear());

        LOGGER.info("Music Box loading - tune in.");
    }
}
