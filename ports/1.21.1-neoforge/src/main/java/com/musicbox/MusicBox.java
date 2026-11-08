package com.musicbox;

import com.mojang.logging.LogUtils;
import com.musicbox.network.ModNetwork;
import com.musicbox.registry.ModBlockEntities;
import com.musicbox.registry.ModBlocks;
import com.musicbox.registry.ModItems;
import com.musicbox.registry.ModMenus;
import com.musicbox.station.StationConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

@Mod(MusicBox.MOD_ID)
public final class MusicBox {

    public static final String MOD_ID = "musicboxradio";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Station indices travel as button ids in a vanilla packet, which bounds the list size. */
    public static final int MAX_STATIONS = 100;

    public MusicBox(IEventBus modBus) {
        StationConfig.load(FMLPaths.CONFIGDIR.get().resolve(MOD_ID));

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ARMOR_MATERIALS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);

        modBus.addListener(ModNetwork::register);

        LOGGER.info("Music Box loading - tune in.");
    }
}
