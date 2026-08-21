package com.musicbox.registry;

import com.musicbox.MusicBox;
import com.musicbox.block.MusicBoxBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MusicBox.MOD_ID);

    public static final RegistryObject<Block> MUSIC_BOX = BLOCKS.register("music_box",
            () -> new MusicBoxBlock(BlockBehaviour.Properties.of(Material.WOOD)
                    .strength(2.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    private ModBlocks() {
    }
}
