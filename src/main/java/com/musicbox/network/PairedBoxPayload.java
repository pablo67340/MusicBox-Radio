package com.musicbox.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * What the server says a player's paired music box is doing right now.
 * <p>
 * Proximity audio can be driven off the block entity, because a client that is close enough
 * to hear a box is close enough to have its chunk. Headphones deliberately outlive that, so
 * the paired box's state has to reach the listener on its own channel.
 */
public record PairedBoxPayload(boolean paired, String dimension, BlockPos pos,
                               String label, String url, boolean playing, float volume) {

    public static final PairedBoxPayload NONE =
            new PairedBoxPayload(false, "", BlockPos.ZERO, "", "", false, 0.0F);

    public static PairedBoxPayload silent(String dimension, BlockPos pos) {
        return new PairedBoxPayload(true, dimension, pos, "", "", false, 0.0F);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(paired);
        if (!paired) {
            return;
        }
        buf.writeUtf(dimension, 256);
        buf.writeBlockPos(pos);
        buf.writeBoolean(playing);
        if (playing) {
            buf.writeUtf(label, 128);
            buf.writeUtf(url, 512);
            buf.writeFloat(volume);
        }
    }

    public static PairedBoxPayload read(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return NONE;
        }
        String dimension = buf.readUtf(256);
        BlockPos pos = buf.readBlockPos();
        if (!buf.readBoolean()) {
            return silent(dimension, pos);
        }
        return new PairedBoxPayload(true, dimension, pos,
                buf.readUtf(128), buf.readUtf(512), true, buf.readFloat());
    }
}
