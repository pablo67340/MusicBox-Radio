package com.musicbox.registry;

import com.musicbox.MusicBox;
import com.musicbox.block.MusicBoxBlock;
import com.musicbox.block.SpeakerBlock;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;

public final class ModBlocks {

    public static final Block MUSIC_BOX = Registry.register(Registry.BLOCK, MusicBox.id("music_box"),
            new MusicBoxBlock(BlockBehaviour.Properties.of(Material.WOOD)
                    .strength(2.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final Block SPEAKER = Registry.register(Registry.BLOCK, MusicBox.id("speaker"),
            new SpeakerBlock(BlockBehaviour.Properties.of(Material.WOOL)
                    .strength(1.5F)
                    .sound(SoundType.WOOL)
                    .noOcclusion()));

    private ModBlocks() {
    }

    /** Touching the class is what runs the registrations above. */
    public static void register() {
    }
}
