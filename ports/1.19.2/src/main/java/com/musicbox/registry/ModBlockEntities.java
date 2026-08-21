package com.musicbox.registry;

import com.musicbox.MusicBox;
import com.musicbox.blockentity.MusicBoxBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MusicBox.MOD_ID);

    public static final RegistryObject<BlockEntityType<MusicBoxBlockEntity>> MUSIC_BOX =
            BLOCK_ENTITIES.register("music_box",
                    () -> BlockEntityType.Builder.of(MusicBoxBlockEntity::new, ModBlocks.MUSIC_BOX.get()).build(null));

    private ModBlockEntities() {
    }
}
