package com.musicbox.registry;

import com.musicbox.block.MusicBoxBlock;
import com.musicbox.block.SpeakerBlock;
import com.musicbox.platform.RegistryRef;
import com.musicbox.platform.Services;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {

    public static final RegistryRef<Block> MUSIC_BOX = Services.PLATFORM.register(Keys.BLOCK, "music_box",
            () -> new MusicBoxBlock(wood("music_box")
                    .strength(2.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final RegistryRef<Block> SPEAKER = Services.PLATFORM.register(Keys.BLOCK, "speaker",
            () -> new SpeakerBlock(wool("speaker")
                    .strength(1.5F)
                    .sound(SoundType.WOOL)
                    .noOcclusion()));

    private ModBlocks() {
    }

    /** Touching the class runs the registrations above. */
    public static void init() {
    }

    private static BlockBehaviour.Properties wood(String name) {
        //? if >=1.20 {
        return withId(name, BlockBehaviour.Properties.of())
                .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                .instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BASS)
                .ignitedByLava();
        //?} else {
        /*return BlockBehaviour.Properties.of(net.minecraft.world.level.material.Material.WOOD);
        *///?}
    }

    private static BlockBehaviour.Properties wool(String name) {
        //? if >=1.20 {
        return withId(name, BlockBehaviour.Properties.of())
                .mapColor(net.minecraft.world.level.material.MapColor.WOOL)
                .instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.GUITAR)
                .ignitedByLava();
        //?} else {
        /*return BlockBehaviour.Properties.of(net.minecraft.world.level.material.Material.WOOL);
        *///?}
    }

    private static BlockBehaviour.Properties withId(String name, BlockBehaviour.Properties properties) {
        //? if >=1.21.2 {
        /*return properties.setId(net.minecraft.resources.ResourceKey.create(Keys.BLOCK, com.musicbox.MusicBox.id(name)));
        *///?} else {
        return properties;
        //?}
    }
}