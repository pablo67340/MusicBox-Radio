package com.musicbox.client.audio;

import com.musicbox.ClientHooks;
import com.musicbox.blockentity.MusicBoxBlockEntity;
import com.musicbox.client.ClientConfig;
import com.musicbox.item.HeadphoneAccess;
import com.musicbox.network.PairedBoxPayload;
import com.musicbox.station.StationConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides, every client tick, which music boxes the local player can hear and how.
 * <p>
 * Proximity audio reads the loaded block entities: if you are close enough to hear a box you
 * are close enough to have its chunk. Headphones deliberately outlive render distance, so the
 * paired box is driven from {@link PairedFeed} - state the server pushes to this client - and
 * needs no block entity at all.
 */
public final class RadioManager implements ClientHooks.BoxListener {

    private static final RadioManager INSTANCE = new RadioManager();

    private final Set<MusicBoxBlockEntity> boxes = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<StreamKey, RadioStream> streams = new HashMap<>();

    private RadioManager() {
    }

    public static RadioManager get() {
        return INSTANCE;
    }

    @Override
    public void boxLoaded(MusicBoxBlockEntity box) {
        boxes.add(box);
    }

    @Override
    public void boxUnloaded(MusicBoxBlockEntity box) {
        boxes.remove(box);
    }

    /** The stream for a box in the player's current dimension, if one is running. */
    @Nullable
    public RadioStream streamAt(BlockPos pos) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        return streams.get(new StreamKey(level.dimension().location().toString(), pos));
    }

    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;

        if (player == null || level == null) {
            stopAll();
            PairedFeed.reset();
            return;
        }
        if (!ClientConfig.streamingEnabled()) {
            stopAll();
            return;
        }

        String dimension = level.dimension().location().toString();
        boxes.removeIf(box -> box.isRemoved() || box.getLevel() != level);

        List<MusicBoxBlockEntity> playing = new ArrayList<>();
        for (MusicBoxBlockEntity box : boxes) {
            if (box.isPlaying()) {
                playing.add(box);
            }
        }

        Vec3 ears = player.getEyePosition();
        List<Audible> audible = new ArrayList<>();
        StreamKey headphones = collectHeadphones(player, dimension, playing, ears, audible);

        double range = StationConfig.proximityRange();
        for (MusicBoxBlockEntity box : playing) {
            BlockPos pos = box.getBlockPos();
            StreamKey key = new StreamKey(dimension, pos);
            if (key.equals(headphones)) {
                // Already head-locked; a second positional copy would phase against it.
                continue;
            }
            double x = pos.getX() + 0.5D;
            double y = pos.getY() + 0.5D;
            double z = pos.getZ() + 0.5D;
            double distance = Math.sqrt(ears.distanceToSqr(x, y, z));
            float falloff = proximityGain(distance, range);
            if (falloff > 0.0F) {
                audible.add(new Audible(key, box.getStationUrl(), AlStreamSource.Mode.PROXIMITY,
                        box.getVolume() * falloff, distance, x, y, z));
            }
        }

        // Headphones always win a slot; everything else competes on distance.
        audible.sort((a, b) -> {
            if (a.mode != b.mode) {
                return a.mode == AlStreamSource.Mode.HEADPHONES ? -1 : 1;
            }
            return Double.compare(a.distance, b.distance);
        });
        int limit = StationConfig.maxConcurrentStreams();
        if (audible.size() > limit) {
            audible = audible.subList(0, limit);
        }

        float categoryVolume = minecraft.options.getSoundSourceVolume(SoundSource.RECORDS);
        Set<StreamKey> wanted = new HashSet<>();

        for (Audible entry : audible) {
            wanted.add(entry.key);

            RadioStream stream = streams.get(entry.key);
            if (stream != null && !stream.url().equals(entry.url)) {
                stream.dispose();
                stream = null;
            }
            if (stream != null && stream.isFailed()) {
                continue;
            }
            if (stream == null) {
                stream = new RadioStream(entry.url);
                streams.put(entry.key, stream);
            }

            float gain = categoryVolume * ClientConfig.masterVolume() * entry.gain;
            stream.tick(entry.mode, gain, entry.x, entry.y, entry.z);
        }

        for (Iterator<Map.Entry<StreamKey, RadioStream>> it = streams.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<StreamKey, RadioStream> entry = it.next();
            if (!wanted.contains(entry.getKey())) {
                entry.getValue().dispose();
                it.remove();
            }
        }
    }

    /**
     * Adds the head-locked stereo entry, if any, and returns its key so the proximity pass can
     * skip that box. A paired box that is switched off means silence, not a fallback: the
     * player asked for that box specifically.
     */
    @Nullable
    private StreamKey collectHeadphones(LocalPlayer player, String dimension,
                                        List<MusicBoxBlockEntity> playing, Vec3 ears,
                                        List<Audible> audible) {
        if (!HeadphoneAccess.isWearing(player)) {
            return null;
        }

        PairedBoxPayload feed = PairedFeed.get();
        if (feed.paired()) {
            if (!feed.playing() || feed.url().isEmpty()) {
                return null;
            }
            StreamKey key = new StreamKey(feed.dimension(), feed.pos());
            audible.add(new Audible(key, feed.url(), AlStreamSource.Mode.HEADPHONES,
                    feed.volume(), 0.0D, 0.0D, 0.0D, 0.0D));
            return key;
        }

        MusicBoxBlockEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (MusicBoxBlockEntity box : playing) {
            BlockPos pos = box.getBlockPos();
            double d = ears.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            if (d < best) {
                best = d;
                nearest = box;
            }
        }
        if (nearest == null) {
            return null;
        }
        StreamKey key = new StreamKey(dimension, nearest.getBlockPos());
        audible.add(new Audible(key, nearest.getStationUrl(), AlStreamSource.Mode.HEADPHONES,
                nearest.getVolume(), 0.0D, 0.0D, 0.0D, 0.0D));
        return key;
    }

    public void stopAll() {
        for (RadioStream stream : streams.values()) {
            stream.dispose();
        }
        streams.clear();
    }

    private static float proximityGain(double distance, double range) {
        double inner = Math.min(3.0D, range * 0.25D);
        if (distance <= inner) {
            return 1.0F;
        }
        if (distance >= range) {
            return 0.0F;
        }
        double t = 1.0D - (distance - inner) / (range - inner);
        return (float) (t * t);
    }

    /** Dimension is part of the key because paired headphones reach across worlds. */
    private record StreamKey(String dimension, BlockPos pos) {
    }

    private record Audible(StreamKey key, String url, AlStreamSource.Mode mode, float gain,
                           double distance, double x, double y, double z) {
    }
}
