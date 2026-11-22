package com.musicbox.client;

import com.musicbox.ClientHooks;
import com.musicbox.MusicBox;
import com.musicbox.client.audio.PairedFeed;
import com.musicbox.client.audio.RadioManager;
import com.musicbox.network.PairedBoxPayload;
import com.musicbox.platform.Services;

/**
 * Client lifecycle hooks; each loader forwards its own events here and registers the
 * screens and block entity renderers itself, as that is where the loaders differ.
 */
public final class ClientSetup {

    private ClientSetup() {
    }

    public static void init() {
        ClientConfig.load(Services.PLATFORM.configDir().resolve(MusicBox.MOD_ID));
        ClientHooks.setListener(RadioManager.get());
    }

    /** Called at the end of every client tick. */
    public static void tick() {
        RadioManager.get().tick();
    }

    public static void receivePairedBox(PairedBoxPayload payload) {
        PairedFeed.accept(payload);
    }
}
