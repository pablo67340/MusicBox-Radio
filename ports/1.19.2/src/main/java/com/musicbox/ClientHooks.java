package com.musicbox;

import com.musicbox.blockentity.MusicBoxBlockEntity;
import com.musicbox.blockentity.SpeakerBlockEntity;

/**
 * Lets common code notify the client audio layer about music boxes and speakers coming and
 * going without naming any client-only class. The client initialiser installs a listener at
 * startup; on a dedicated server nothing ever registers one and every call is a no-op.
 */
public final class ClientHooks {

    public interface BoxListener {
        void boxLoaded(MusicBoxBlockEntity box);

        void boxUnloaded(MusicBoxBlockEntity box);

        void speakerLoaded(SpeakerBlockEntity speaker);

        void speakerUnloaded(SpeakerBlockEntity speaker);
    }

    private static BoxListener listener;

    private ClientHooks() {
    }

    public static void setListener(BoxListener boxListener) {
        listener = boxListener;
    }

    public static void boxLoaded(MusicBoxBlockEntity box) {
        if (listener != null) {
            listener.boxLoaded(box);
        }
    }

    public static void boxUnloaded(MusicBoxBlockEntity box) {
        if (listener != null) {
            listener.boxUnloaded(box);
        }
    }

    public static void speakerLoaded(SpeakerBlockEntity speaker) {
        if (listener != null) {
            listener.speakerLoaded(speaker);
        }
    }

    public static void speakerUnloaded(SpeakerBlockEntity speaker) {
        if (listener != null) {
            listener.speakerUnloaded(speaker);
        }
    }
}
