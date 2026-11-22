package com.musicbox.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** World lookups whose names moved between Minecraft versions. */
public final class Levels {

    private Levels() {
    }

    public static Level of(Entity entity) {
        //? if >=1.20 {
        return entity.level();
        //?} else {
        /*return entity.level;
        *///?}
    }

    public static ResourceLocation dimensionId(Level level) {
        return level.dimension().location();
    }

    @Nullable
    public static ServerLevel byId(MinecraftServer server, ResourceLocation dimension) {
        //? if >=1.19.3 {
        return server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimension));
        //?} else {
        /*return server.getLevel(ResourceKey.create(net.minecraft.core.Registry.DIMENSION_REGISTRY, dimension));
        *///?}
    }
}
