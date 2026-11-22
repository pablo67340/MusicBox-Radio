package com.musicbox;

import com.mojang.logging.LogUtils;
import com.musicbox.network.ModNetwork;
import com.musicbox.platform.Services;
import com.musicbox.registry.ModBlockEntities;
import com.musicbox.registry.ModBlocks;
import com.musicbox.registry.ModItems;
import com.musicbox.registry.ModMenus;
import com.musicbox.station.StationConfig;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public final class MusicBox {

    public static final String MOD_ID = "musicboxradio";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Station indices travel as button ids in a vanilla packet, which bounds the list size. */
    public static final int MAX_STATIONS = 100;

    private MusicBox() {
    }

    /** Called once by each loader's entry point, before its registration phase. */
    public static void init() {
        StationConfig.load(Services.PLATFORM.configDir().resolve(MOD_ID));

        ModBlocks.init();
        ModItems.init();
        ModBlockEntities.init();
        ModMenus.init();
        ModNetwork.init();

        LOGGER.info("Music Box loading - tune in.");
    }

    public static ResourceLocation id(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        //?} else {
        /*return new ResourceLocation(MOD_ID, path);
        *///?}
    }
}
