package com.musicbox.client.audio;

import com.musicbox.MusicBox;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.system.MemoryUtil;

import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenAL playback for one decoded stream, living inside Minecraft's existing AL context.
 * <p>
 * Every method here must be called from the render thread, which is the only thread the
 * context is current on.
 * <p>
 * One decoded stream can come out of several places at once - a music box and the speakers
 * paired to it - so this owns a set of <em>emitters</em>, each a position in the world with
 * its own gain. They are all handed the very same chunks in the same order and are started
 * deliberately, because two emitters a few hundred milliseconds apart in the same room comb
 * filter into something that sounds like a broken echo. Sharing one decoder also keeps the
 * mod down to a single connection per station, which matters when stations cap connections
 * per address.
 * <p>
 * OpenAL only spatialises <em>mono</em> buffers; hand it stereo and it routes the channels
 * straight to the output untouched, ignoring position entirely. That single constraint shapes
 * all three layouts below:
 * <ul>
 *   <li><b>Headphones</b> want the stereo image and no positioning, so one stereo voice is
 *       exactly right.</li>
 *   <li><b>Proximity on a stereo station</b> gets two mono voices per emitter, one per
 *       channel, placed a short distance either side of the block. Both are spatialised, so
 *       distance and surround placement still work, and the stereo image survives rather than
 *       being summed away. Downmixing to a single mono voice used to phase-cancel anything
 *       stereo-widened, which is what made wide synth material sound thin.</li>
 *   <li><b>Proximity on a mono station</b> is a single mono voice, since there is no image to
 *       preserve.</li>
 * </ul>
 */
final class AlStreamSource {

    enum Mode {
        /** Positioned in the world, attenuated by distance. */
        PROXIMITY,
        /** Head-locked stereo, audible at any range. */
        HEADPHONES
    }

    /** One place the stream should come out of. */
    record Target(Object key, float gain, Vec3 pos) {
    }

    /** Chunks kept in flight per voice; ~0.74 s of audio. */
    private static final int TARGET_QUEUED = 8;

    /** Chunks required before the first emitter begins, to absorb network jitter. */
    private static final int PREBUFFER = 5;

    /**
     * How far apart the two proximity voices sit, in blocks. Wide enough to read as a real
     * stereo image, narrow enough that the box still sounds like one object.
     */
    private static final double SEPARATION = 1.25D;

    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

    private final Map<Object, Emitter> emitters = new LinkedHashMap<>();

    private Mode mode;
    private int sourceChannels;
    private int sourceRate;
    private boolean splitStereo;
    private boolean allocationFailed;
    private boolean feedRunning;

    private final Spectrum analyser = new Spectrum();
    private final SpectrumFeed feed = new SpectrumFeed();

    boolean isBuffering() {
        for (Emitter emitter : emitters.values()) {
            if (emitter.started) {
                return false;
            }
        }
        return true;
    }

    SpectrumFeed feed() {
        return feed;
    }

    /**
     * Moves audio from the decoder onto every emitter.
     *
     * @param listener world position of the player's ears, used to spread the stereo pair
     *                 across the listener's view rather than along a fixed world axis
     * @return false if a source could not be created
     */
    boolean pump(StreamDecoder decoder, Mode desiredMode, List<Target> targets, Vec3 listener) {
        int channels = decoder.channels();
        int rate = decoder.sampleRate();
        if (channels < 1 || rate < 1) {
            return true;
        }

        if (mode != desiredMode || sourceChannels != channels || sourceRate != rate) {
            // The AL format is baked into every queued buffer, so any format or layout change
            // means tearing the voices down and starting again.
            destroy();
            mode = desiredMode;
            sourceChannels = channels;
            sourceRate = rate;
            splitStereo = desiredMode == Mode.PROXIMITY && channels == 2;
            feed.configure(rate);
        }

        if (!reconcile(targets)) {
            return false;
        }
        if (emitters.isEmpty()) {
            return true;
        }

        Emitter leader = leader();
        for (Emitter emitter : emitters.values()) {
            int processed = emitter.recycleProcessed();
            if (emitter == leader && processed > 0) {
                feed.onBuffersProcessed(processed, System.nanoTime());
            }
        }

        while (hasRoom()) {
            short[] chunk = decoder.poll();
            if (chunk == null) {
                break;
            }
            analyse(chunk, channels);
            for (Emitter emitter : emitters.values()) {
                emitter.accept(chunk, channels);
            }
        }

        applyMix(targets, listener);
        startAndResume();
        return true;
    }

    /** Creates and drops emitters so the live set matches what the manager asked for. */
    private boolean reconcile(List<Target> targets) {
        Set<Object> wanted = new HashSet<>(targets.size());
        for (Target target : targets) {
            wanted.add(target.key());
        }
        emitters.entrySet().removeIf(entry -> {
            if (wanted.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().destroy();
            return true;
        });

        for (Target target : targets) {
            if (emitters.containsKey(target.key())) {
                continue;
            }
            Emitter emitter = new Emitter(splitStereo ? 2 : 1);
            if (!emitter.create(mode == Mode.HEADPHONES)) {
                emitter.destroy();
                if (!allocationFailed) {
                    // Latched, because pump() retries every tick and this would flood the log.
                    allocationFailed = true;
                    MusicBox.LOGGER.warn("Music Box could not allocate an OpenAL source; too many sounds playing?");
                }
                return !emitters.isEmpty();
            }
            emitters.put(target.key(), emitter);
        }
        allocationFailed = false;
        return true;
    }

    /**
     * An emitter that joins an already-running stream has to fill to the same depth as one
     * that is already playing before it starts, not merely to the prebuffer. Starting early
     * would leave it permanently ahead of the others by the difference.
     */
    private void startAndResume() {
        Emitter leader = leader();
        int required = leader == null ? PREBUFFER : Math.max(PREBUFFER, leader.depth());

        for (Emitter emitter : emitters.values()) {
            if (!emitter.started) {
                if (emitter.depth() >= required) {
                    emitter.play();
                    if (!feedRunning) {
                        feedRunning = true;
                        feed.onStarted(System.nanoTime());
                    }
                }
            } else {
                emitter.resumeIfStalled();
            }
        }
    }

    private Emitter leader() {
        for (Emitter emitter : emitters.values()) {
            if (emitter.started) {
                return emitter;
            }
        }
        return null;
    }

    /**
     * Feeding is paced by the emitters that are already playing. An emitter still catching up
     * rides along on the same chunks and is deliberately allowed to grow past them.
     */
    private boolean hasRoom() {
        if (emitters.isEmpty()) {
            return false;
        }
        Emitter leader = leader();
        if (leader == null) {
            for (Emitter emitter : emitters.values()) {
                if (emitter.depth() >= TARGET_QUEUED) {
                    return false;
                }
            }
            return true;
        }
        for (Emitter emitter : emitters.values()) {
            if (emitter.started && emitter.depth() >= TARGET_QUEUED) {
                return false;
            }
        }
        return true;
    }

    void destroy() {
        for (Emitter emitter : emitters.values()) {
            emitter.destroy();
        }
        emitters.clear();
        feed.stopped();
        feedRunning = false;
        mode = null;
        sourceChannels = 0;
        sourceRate = 0;
        splitStereo = false;
    }

    /** Splits the chunk into analysis windows so the meter can move faster than the buffer. */
    private void analyse(short[] chunk, int channels) {
        float[][] windows = new float[SpectrumFeed.FRAMES_PER_BUFFER][];
        for (int i = 0; i < windows.length; i++) {
            windows[i] = analyser.analyse(chunk, i * Spectrum.WINDOW, channels, sourceRate);
        }
        feed.push(windows);
    }

    private void applyMix(List<Target> targets, Vec3 listener) {
        for (Target target : targets) {
            Emitter emitter = emitters.get(target.key());
            if (emitter == null) {
                continue;
            }
            emitter.gain(target.gain());

            if (mode == Mode.HEADPHONES) {
                emitter.position(0, Vec3.ZERO);
                continue;
            }
            if (!splitStereo) {
                emitter.position(0, target.pos());
                continue;
            }

            // Spread the pair perpendicular to the line of sight, so the image stays wide from
            // wherever the player happens to be standing instead of collapsing at certain angles.
            Vec3 offset = target.pos().subtract(listener).cross(UP);
            double length = offset.length();
            offset = (length < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : offset.scale(1.0D / length))
                    .scale(SEPARATION * 0.5D);

            emitter.position(0, target.pos().subtract(offset));
            emitter.position(1, target.pos().add(offset));
        }
    }

    /** One place in the world the stream comes out of, and the AL voices that do it. */
    private final class Emitter {

        private final Voice[] voices;
        private boolean started;

        Emitter(int count) {
            this.voices = new Voice[count];
            for (int i = 0; i < count; i++) {
                voices[i] = new Voice();
            }
        }

        boolean create(boolean headLocked) {
            for (Voice voice : voices) {
                if (!voice.create(headLocked)) {
                    return false;
                }
            }
            return true;
        }

        void accept(short[] chunk, int channels) {
            if (splitStereo) {
                voices[0].queueDeinterleaved(chunk, 0, sourceRate);
                voices[1].queueDeinterleaved(chunk, 1, sourceRate);
            } else if (mode == Mode.HEADPHONES) {
                voices[0].queueStereo(chunk, channels, sourceRate);
            } else {
                voices[0].queueMono(chunk, channels, sourceRate);
            }
        }

        /** The shallowest voice, since playback can only start once every voice is primed. */
        int depth() {
            int depth = Integer.MAX_VALUE;
            for (Voice voice : voices) {
                depth = Math.min(depth, voice.queued.size());
            }
            return depth == Integer.MAX_VALUE ? 0 : depth;
        }

        void play() {
            // Started together so the pair stays sample-aligned from the first buffer.
            for (Voice voice : voices) {
                AL10.alSourcePlay(voice.source);
            }
            started = true;
        }

        void resumeIfStalled() {
            for (Voice voice : voices) {
                if (AL10.alGetSourcei(voice.source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING
                        && !voice.queued.isEmpty()) {
                    // Underran while the network caught up; resume with whatever we have.
                    AL10.alSourcePlay(voice.source);
                }
            }
        }

        int recycleProcessed() {
            int processed = 0;
            for (Voice voice : voices) {
                processed = Math.max(processed, voice.recycleProcessed());
            }
            return processed;
        }

        void gain(float value) {
            float clamped = Math.max(0.0F, Math.min(1.0F, value));
            for (Voice voice : voices) {
                AL10.alSourcef(voice.source, AL10.AL_GAIN, clamped);
            }
        }

        void position(int index, Vec3 at) {
            if (index < voices.length) {
                AL10.alSource3f(voices[index].source, AL10.AL_POSITION,
                        (float) at.x, (float) at.y, (float) at.z);
            }
        }

        void destroy() {
            for (Voice voice : voices) {
                voice.destroy();
            }
            started = false;
        }
    }

    /** One OpenAL source plus the buffers currently queued on it. */
    private static final class Voice {

        private int source;
        private ShortBuffer scratch;
        private final Deque<Integer> queued = new ArrayDeque<>();

        boolean create(boolean headLocked) {
            AL10.alGetError();
            source = AL10.alGenSources();
            if (AL10.alGetError() != AL10.AL_NO_ERROR || source == 0) {
                source = 0;
                return false;
            }

            AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
            // Distance falloff is computed in Java so the curve is identical regardless of
            // which distance model Minecraft has set globally. AL still handles panning from
            // AL_POSITION, which is the part we actually want from it.
            AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0F);
            AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 0.0F);
            AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, Float.MAX_VALUE);
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, headLocked ? AL10.AL_TRUE : AL10.AL_FALSE);

            scratch = MemoryUtil.memAllocShort(StreamDecoder.CHUNK_FRAMES * 2);
            return true;
        }

        /** Pulls one channel out of an interleaved stereo chunk. */
        void queueDeinterleaved(short[] chunk, int channel, int rate) {
            scratch.clear();
            for (int i = channel; i < chunk.length; i += 2) {
                scratch.put(chunk[i]);
            }
            submit(AL10.AL_FORMAT_MONO16, rate);
        }

        void queueMono(short[] chunk, int channels, int rate) {
            scratch.clear();
            if (channels == 1) {
                scratch.put(chunk);
            } else {
                for (int i = 0; i + 1 < chunk.length; i += 2) {
                    scratch.put((short) ((chunk[i] + chunk[i + 1]) / 2));
                }
            }
            submit(AL10.AL_FORMAT_MONO16, rate);
        }

        void queueStereo(short[] chunk, int channels, int rate) {
            scratch.clear();
            if (channels == 2) {
                scratch.put(chunk);
            } else {
                for (short sample : chunk) {
                    scratch.put(sample).put(sample);
                }
            }
            submit(AL10.AL_FORMAT_STEREO16, rate);
        }

        private void submit(int format, int rate) {
            scratch.flip();
            int buffer = AL10.alGenBuffers();
            AL10.alBufferData(buffer, format, scratch, rate);
            AL10.alSourceQueueBuffers(source, buffer);
            queued.addLast(buffer);
        }

        /** @return how many buffers finished playing since the last call */
        int recycleProcessed() {
            if (source == 0) {
                return 0;
            }
            int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
            int count = processed;
            while (processed-- > 0) {
                int buffer = AL10.alSourceUnqueueBuffers(source);
                queued.remove(buffer);
                AL10.alDeleteBuffers(buffer);
            }
            return count;
        }

        void destroy() {
            if (source != 0) {
                AL10.alSourceStop(source);
                recycleProcessed();
                for (Integer buffer : queued) {
                    AL10.alDeleteBuffers(buffer);
                }
                queued.clear();
                AL10.alDeleteSources(source);
                source = 0;
            }
            if (scratch != null) {
                MemoryUtil.memFree(scratch);
                scratch = null;
            }
        }
    }
}
