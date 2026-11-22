package com.musicbox.util;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;

public final class Interactions {

    private Interactions() {
    }

    /** The interaction was handled; swings the arm on the client and consumes it on the server. */
    public static InteractionResult success(Level level) {
        //? if >=1.21.2 {
        /*return InteractionResult.SUCCESS;
        *///?} else {
        return InteractionResult.sidedSuccess(level.isClientSide);
        //?}
    }
}
