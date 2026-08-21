package com.musicbox.registry;

import com.musicbox.MusicBox;
import com.musicbox.blockentity.MusicBoxBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {

    public static final BlockEntityType<MusicBoxBlockEntity> MUSIC_BOX = Registry.register(
            Registry.BLOCK_ENTITY_TYPE, MusicBox.id("music_box"),
            FabricBlockEntityTypeBuilder.create(MusicBoxBlockEntity::new, ModBlocks.MUSIC_BOX).build());

    private ModBlockEntities() {
    }

    public static void register() {
    }
}
