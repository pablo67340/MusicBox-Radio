package com.musicbox.client.audio;

import com.musicbox.MusicBox;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.system.MemoryUtil;

import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * An OpenAL streaming source living inside Minecraft's existing AL context.
 * <p>
 * Every method here must be called from the render thread, which is the only thread the
 * context is current on.
 * <p>
 * OpenAL only spatialises <em>mono</em> buffers; hand it stereo and it routes the channels
 * straight to the output untouched, ignoring position entirely. That single constraint shapes
 * all three layouts below:
 * <ul>
 *   <li><b>Headphones</b> want the stereo image and no positioning, so one stereo voice is
 *       exactly right.</li>
 *   <li><b>Proximity on a stereo station</b> gets two mono voices, one per channel, placed a
 *       short distance either side of the block. Both are spatialised, so distance and surround
 *       placement still work, and the stereo image survives rather than being summed away.
 *       Downmixing to a single mono voice used to phase-cancel anything stereo-widened, which
 *       is what made wide synth material sound thin.</li>
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

    /** Chunks kept in flight per voice; ~0.74 s of audio. */
    private static final int TARGET_QUEUED = 8;

    /** Chunks required before playback begins, to absorb network jitter. */
    private static final int PREBUFFER = 5;

    /**
     * How far apart the two proximity voices sit, in blocks. Wide enough to read as a real
     * stereo image, narrow enough that the box still sounds like one object.
     */
    private static final double SEPARATION = 1.25D;

    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

    private Voice[] voices = new Voice[0];

    private Mode mode;
    private int sourceChannels;
    private int sourceRate;
    private boolean splitStereo;
    private boolean started;
    private boolean allocationFailed;

    boolean isBuffering() {
        return !started;
    }

    /**
     * Moves audio from the decoder onto the AL voices.
     *
     * @param pos      world position of the block
     * @param listener world position of the player's ears, used to spread the stereo pair
     *                 across the listener's view rather than along a fixed world axis
     * @return false if a source could not be created
     */
    boolean pump(StreamDecoder decoder, Mode desiredMode, float gain, Vec3 pos, Vec3 listener) {
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
        }

        if (voices.length == 0 && !create()) {
            return false;
        }

        for (Voice voice : voices) {
            voice.recycleProcessed();
        }

        while (hasRoom()) {
            short[] chunk = decoder.poll();
            if (chunk == null) {
                break;
            }
            distribute(chunk, channels);
        }

        applyMix(gain, pos, listener);

        if (!started) {
            if (bufferedDepth() >= PREBUFFER) {
                // Started together so the pair stays sample-aligned from the first buffer.
                for (Voice voice : voices) {
                    AL10.alSourcePlay(voice.source);
                }
                started = true;
            }
        } else {
            for (Voice voice : voices) {
                if (AL10.alGetSourcei(voice.source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING
                        && !voice.queued.isEmpty()) {
                    // Underran while the network caught up; resume with whatever we have.
                    AL10.alSourcePlay(voice.source);
                }
            }
        }

        return true;
    }

    void destroy() {
        for (Voice voice : voices) {
            voice.destroy();
        }
        voices = new Voice[0];
        started = false;
        mode = null;
        sourceChannels = 0;
        sourceRate = 0;
        splitStereo = false;
    }

    private boolean create() {
        int count = splitStereo ? 2 : 1;
        Voice[] created = new Voice[count];
        for (int i = 0; i < count; i++) {
            Voice voice = new Voice();
            if (!voice.create(mode == Mode.HEADPHONES)) {
                for (int j = 0; j < i; j++) {
                    created[j].destroy();
                }
                if (!allocationFailed) {
                    // Latched, because pump() retries every tick and this would flood the log.
                    allocationFailed = true;
                    MusicBox.LOGGER.warn("Music Box could not allocate an OpenAL source; too many sounds playing?");
                }
                return false;
            }
            created[i] = voice;
        }
        allocationFailed = false;
        voices = created;
        return true;
    }

    private boolean hasRoom() {
        for (Voice voice : voices) {
            if (voice.queued.size() >= TARGET_QUEUED) {
                return false;
            }
        }
        return voices.length > 0;
    }

    /** The shallowest voice, since playback can only start once every voice is primed. */
    private int bufferedDepth() {
        int depth = Integer.MAX_VALUE;
        for (Voice voice : voices) {
            depth = Math.min(depth, voice.queued.size());
        }
        return depth == Integer.MAX_VALUE ? 0 : depth;
    }

    private void distribute(short[] chunk, int channels) {
        if (splitStereo) {
            voices[0].queueDeinterleaved(chunk, 0, sourceRate);
            voices[1].queueDeinterleaved(chunk, 1, sourceRate);
        } else if (mode == Mode.HEADPHONES) {
            voices[0].queueStereo(chunk, channels, sourceRate);
        } else {
            voices[0].queueMono(chunk, channels, sourceRate);
        }
    }

    private void applyMix(float gain, Vec3 pos, Vec3 listener) {
        float clamped = Math.max(0.0F, Math.min(1.0F, gain));
        for (Voice voice : voices) {
            AL10.alSourcef(voice.source, AL10.AL_GAIN, clamped);
        }

        if (mode == Mode.HEADPHONES) {
            AL10.alSource3f(voices[0].source, AL10.AL_POSITION, 0.0F, 0.0F, 0.0F);
            return;
        }

        if (!splitStereo) {
            position(voices[0], pos);
            return;
        }

        // Spread the pair perpendicular to the line of sight, so the image stays wide from
        // wherever the player happens to be standing instead of collapsing at certain angles.
        Vec3 offset = pos.subtract(listener).cross(UP);
        double length = offset.length();
        offset = (length < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : offset.scale(1.0D / length))
                .scale(SEPARATION * 0.5D);

        position(voices[0], pos.subtract(offset));
        position(voices[1], pos.add(offset));
    }

    private static void position(Voice voice, Vec3 at) {
        AL10.alSource3f(voice.source, AL10.AL_POSITION, (float) at.x, (float) at.y, (float) at.z);
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

        void recycleProcessed() {
            if (source == 0) {
                return;
            }
            int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
            while (processed-- > 0) {
                int buffer = AL10.alSourceUnqueueBuffers(source);
                queued.remove(buffer);
                AL10.alDeleteBuffers(buffer);
            }
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
