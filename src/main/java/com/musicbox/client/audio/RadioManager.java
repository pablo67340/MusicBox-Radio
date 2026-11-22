package com.musicbox.client.audio;

import com.musicbox.ClientHooks;
import com.musicbox.blockentity.MusicBoxBlockEntity;
import com.musicbox.blockentity.SpeakerBlockEntity;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides, every client tick, which stations the local player can hear and from where.
 * <p>
 * Streams are keyed by station URL rather than by block, because a music box and the
 * speakers paired to it are all one piece of audio coming out of several places. Sharing the
 * decoder that way is what keeps them in sync with each other and holds the mod to one
 * connection per station no matter how many blocks are playing it.
 * <p>
 * Proximity audio reads the loaded block entities: if you are close enough to hear something
 * you are close enough to have its chunk. Headphones deliberately outlive render distance, so
 * the paired box is driven from {@link PairedFeed} - state the server pushes to this client -
 * and needs no block entity at all.
 */
public final class RadioManager implements ClientHooks.BoxListener {

    private static final RadioManager INSTANCE = new RadioManager();

    /** Ceiling on emitters for one station, so a wall of speakers cannot exhaust AL sources. */
    private static final int MAX_EMITTERS_PER_STREAM = 6;

    private final Set<MusicBoxBlockEntity> boxes = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<SpeakerBlockEntity> speakers = Collections.newSetFromMap(new IdentityHashMap<>());

    private final Map<String, RadioStream> streams = new HashMap<>();
    private final Map<StreamKey, RadioStream> byPosition = new HashMap<>();

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

    @Override
    public void speakerLoaded(SpeakerBlockEntity speaker) {
        speakers.add(speaker);
    }

    @Override
    public void speakerUnloaded(SpeakerBlockEntity speaker) {
        speakers.remove(speaker);
    }

    /** The stream feeding a given block, if this client is playing it. Used by the renderers. */
    @Nullable
    public RadioStream streamAt(BlockPos pos) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        return byPosition.get(new StreamKey(level.dimension().location().toString(), pos));
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
        speakers.removeIf(speaker -> speaker.isRemoved() || speaker.getLevel() != level);

        Vec3 ears = player.getEyePosition();
        List<Emitter> emitters = new ArrayList<>();
        String headphoneUrl = collectHeadphones(player, dimension, ears, emitters);
        collectProximity(dimension, ears, headphoneUrl, emitters);

        playSelected(minecraft, group(emitters, headphoneUrl), ears);
    }

    /** Everything audible from a position: boxes on their own, and speakers relaying a box. */
    private void collectProximity(String dimension, Vec3 ears, @Nullable String headphoneUrl,
                                  List<Emitter> emitters) {
        double range = StationConfig.proximityRange();

        for (MusicBoxBlockEntity box : boxes) {
            if (box.isPlaying()) {
                add(emitters, dimension, box.getBlockPos(), box.getStationUrl(), box.getVolume(),
                        ears, range, headphoneUrl);
            }
        }
        for (SpeakerBlockEntity speaker : speakers) {
            if (speaker.isPlaying()) {
                add(emitters, dimension, speaker.getBlockPos(), speaker.getStationUrl(),
                        speaker.getVolume(), ears, range, headphoneUrl);
            }
        }
    }

    private void add(List<Emitter> emitters, String dimension, BlockPos pos, String url,
                     float volume, Vec3 ears, double range, @Nullable String headphoneUrl) {
        // Headphones replace the room rather than layering over it. One stream can only be
        // head-locked or positional, so anything on that station stays quiet while they are on.
        if (url.equals(headphoneUrl)) {
            return;
        }
        Vec3 centre = Vec3.atCenterOf(pos);
        double distance = ears.distanceTo(centre);
        float falloff = proximityGain(distance, range);
        if (falloff <= 0.0F) {
            return;
        }
        emitters.add(new Emitter(new StreamKey(dimension, pos), url, AlStreamSource.Mode.PROXIMITY,
                volume * falloff, distance, centre));
    }

    /**
     * Adds the head-locked stereo emitter, if any, and returns its station so the proximity
     * pass can leave it alone. A paired box that is switched off means silence, not a
     * fallback: the player asked for that box specifically.
     */
    @Nullable
    private String collectHeadphones(LocalPlayer player, String dimension, Vec3 ears,
                                     List<Emitter> emitters) {
        if (!HeadphoneAccess.isWearing(player)) {
            return null;
        }

        PairedBoxPayload feed = PairedFeed.get();
        if (feed.paired()) {
            if (!feed.playing() || feed.url().isEmpty()) {
                return null;
            }
            emitters.add(new Emitter(new StreamKey(feed.dimension(), feed.pos()), feed.url(),
                    AlStreamSource.Mode.HEADPHONES, feed.volume(), 0.0D, Vec3.ZERO));
            return feed.url();
        }

        MusicBoxBlockEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (MusicBoxBlockEntity box : boxes) {
            if (!box.isPlaying()) {
                continue;
            }
            double distance = ears.distanceToSqr(Vec3.atCenterOf(box.getBlockPos()));
            if (distance < best) {
                best = distance;
                nearest = box;
            }
        }
        if (nearest == null) {
            return null;
        }
        emitters.add(new Emitter(new StreamKey(dimension, nearest.getBlockPos()), nearest.getStationUrl(),
                AlStreamSource.Mode.HEADPHONES, nearest.getVolume(), 0.0D, Vec3.ZERO));
        return nearest.getStationUrl();
    }

    /**
     * Buckets emitters by station and decides which stations make the cut, nearest first with
     * headphones always kept.
     */
    private Map<String, List<Emitter>> group(List<Emitter> emitters, @Nullable String headphoneUrl) {
        Map<String, List<Emitter>> byUrl = new HashMap<>();
        for (Emitter emitter : emitters) {
            byUrl.computeIfAbsent(emitter.url(), key -> new ArrayList<>()).add(emitter);
        }

        List<String> urls = new ArrayList<>(byUrl.keySet());
        urls.sort(Comparator.comparingDouble(url -> {
            if (url.equals(headphoneUrl)) {
                return -1.0D;
            }
            double nearest = Double.MAX_VALUE;
            for (Emitter emitter : byUrl.get(url)) {
                nearest = Math.min(nearest, emitter.distance());
            }
            return nearest;
        }));

        int limit = StationConfig.maxConcurrentStreams();
        Map<String, List<Emitter>> selected = new LinkedHashMap<>();
        for (String url : urls.subList(0, Math.min(limit, urls.size()))) {
            List<Emitter> group = byUrl.get(url);
            group.sort(Comparator.comparingDouble(Emitter::distance));
            if (group.size() > MAX_EMITTERS_PER_STREAM) {
                group = group.subList(0, MAX_EMITTERS_PER_STREAM);
            }
            selected.put(url, group);
        }
        return selected;
    }

    private void playSelected(Minecraft minecraft, Map<String, List<Emitter>> selected, Vec3 ears) {
        float categoryVolume = minecraft.options.getSoundSourceVolume(SoundSource.RECORDS);
        float master = categoryVolume * ClientConfig.masterVolume();

        byPosition.clear();
        for (Map.Entry<String, List<Emitter>> entry : selected.entrySet()) {
            String url = entry.getKey();
            List<Emitter> group = entry.getValue();

            RadioStream stream = streams.get(url);
            if (stream != null && stream.isFailed()) {
                continue;
            }
            if (stream == null) {
                stream = new RadioStream(url);
                streams.put(url, stream);
            }

            List<AlStreamSource.Target> targets = new ArrayList<>(group.size());
            for (Emitter emitter : group) {
                targets.add(new AlStreamSource.Target(emitter.key(), master * emitter.gain(), emitter.pos()));
                byPosition.put(emitter.key(), stream);
            }
            stream.tick(group.get(0).mode(), targets, ears);
        }

        for (Iterator<Map.Entry<String, RadioStream>> it = streams.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, RadioStream> entry = it.next();
            if (!selected.containsKey(entry.getKey())) {
                entry.getValue().dispose();
                it.remove();
            }
        }
    }

    public void stopAll() {
        for (RadioStream stream : streams.values()) {
            stream.dispose();
        }
        streams.clear();
        byPosition.clear();
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

    /** One block asking to be heard, before stations are bucketed and capped. */
    private record Emitter(StreamKey key, String url, AlStreamSource.Mode mode, float gain,
                           double distance, Vec3 pos) {
    }
}
