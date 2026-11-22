package com.musicbox.registry;

import com.musicbox.blockentity.MusicBoxBlockEntity;
import com.musicbox.blockentity.SpeakerBlockEntity;
import com.musicbox.platform.RegistryRef;
import com.musicbox.platform.Services;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {

    public static final RegistryRef<BlockEntityType<MusicBoxBlockEntity>> MUSIC_BOX =
            Services.PLATFORM.register(Keys.BLOCK_ENTITY_TYPE, "music_box",
                    () -> BlockEntityType.Builder.of(MusicBoxBlockEntity::new, ModBlocks.MUSIC_BOX.get()).build(null));

    public static final RegistryRef<BlockEntityType<SpeakerBlockEntity>> SPEAKER =
            Services.PLATFORM.register(Keys.BLOCK_ENTITY_TYPE, "speaker",
                    () -> BlockEntityType.Builder.of(SpeakerBlockEntity::new, ModBlocks.SPEAKER.get()).build(null));

    private ModBlockEntities() {
    }

    /** Touching the class runs the registrations above. */
    public static void init() {
    }
}
