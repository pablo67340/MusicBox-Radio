package com.musicbox.platform;

public final class Services {

    public static final Platform PLATFORM =
            //? if fabric {
            /*new com.musicbox.platform.fabric.FabricPlatform();
            *///?} else if forge {
            /*new com.musicbox.platform.forge.ForgePlatform();
            *///?} else {
            new com.musicbox.platform.neoforge.NeoForgePlatform();
            //?}

    private Services() {
    }
}
