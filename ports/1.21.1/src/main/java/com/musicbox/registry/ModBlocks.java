package com.musicbox.registry;

import com.musicbox.MusicBox;
import com.musicbox.block.MusicBoxBlock;
import com.musicbox.block.SpeakerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MusicBox.MOD_ID);

    public static final RegistryObject<Block> MUSIC_BOX = BLOCKS.register("music_box",
            () -> new MusicBoxBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.0F)
                    .sound(SoundType.WOOD)
                    .ignitedByLava()
                    .noOcclusion()));

    public static final RegistryObject<Block> SPEAKER = BLOCKS.register("speaker",
            () -> new SpeakerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOL)
                    .instrument(NoteBlockInstrument.GUITAR)
                    .strength(1.5F)
                    .sound(SoundType.WOOL)
                    .ignitedByLava()
                    .noOcclusion()));

    private ModBlocks() {
    }
}
