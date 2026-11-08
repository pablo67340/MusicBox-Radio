package com.musicbox.registry;

import com.musicbox.MusicBox;
import com.musicbox.blockentity.MusicBoxBlockEntity;
import com.musicbox.blockentity.SpeakerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MusicBox.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MusicBoxBlockEntity>> MUSIC_BOX =
            BLOCK_ENTITIES.register("music_box",
                    () -> BlockEntityType.Builder.of(MusicBoxBlockEntity::new, ModBlocks.MUSIC_BOX.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpeakerBlockEntity>> SPEAKER =
            BLOCK_ENTITIES.register("speaker",
                    () -> BlockEntityType.Builder.of(SpeakerBlockEntity::new, ModBlocks.SPEAKER.get()).build(null));

    private ModBlockEntities() {
    }
}
