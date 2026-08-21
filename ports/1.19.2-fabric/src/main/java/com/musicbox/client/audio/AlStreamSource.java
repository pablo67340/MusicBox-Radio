package com.musicbox.client.audio;

import com.musicbox.MusicBox;
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
 * The playback mode decides the buffer format, and the format is what gives us the two
 * behaviours the mod needs. OpenAL only spatialises <em>mono</em> sources; stereo buffers are
 * routed straight to the left and right output channels untouched. So proximity playback
 * downmixes to mono and gets positional audio for free, while headphone playback keeps the
 * original stereo image at full fidelity and simply ignores where the block is.
 */
final class AlStreamSource {

    enum Mode {
        /** Mono, positioned in the world, attenuated by distance. */
        PROXIMITY,
        /** Stereo, head-locked, audible at any range. */
        HEADPHONES
    }

    /** Chunks kept in flight on the source; ~0.5 s of audio. */
    private static final int TARGET_QUEUED = 6;

    /** Chunks required before playback begins, to absorb network jitter. */
    private static final int PREBUFFER = 4;

    private int source;
    private final Deque<Integer> queuedBuffers = new ArrayDeque<>();
    private ShortBuffer scratch;

    private Mode mode;
    private int sourceChannels;
    private int sourceRate;
    private boolean started;
    private boolean allocationFailed;

    boolean isBuffering() {
        return !started;
    }

    /**
     * Moves audio from the decoder onto the AL source.
     *
     * @return false if the source could not be created
     */
    boolean pump(StreamDecoder decoder, Mode desiredMode, float gain, double x, double y, double z) {
        int channels = decoder.channels();
        int rate = decoder.sampleRate();
        if (channels < 1 || rate < 1) {
            return true;
        }

        boolean formatChanged = mode != desiredMode || sourceChannels != channels || sourceRate != rate;
        if (formatChanged) {
            // The AL format is baked into every queued buffer, so a mode flip means a fresh source.
            destroy();
            mode = desiredMode;
            sourceChannels = channels;
            sourceRate = rate;
        }

        if (source == 0 && !create()) {
            return false;
        }

        recycleProcessedBuffers();

        while (queuedBuffers.size() < TARGET_QUEUED) {
            short[] chunk = decoder.poll();
            if (chunk == null) {
                break;
            }
            queueChunk(chunk, channels);
        }

        applyMix(gain, x, y, z);

        if (!started) {
            if (queuedBuffers.size() >= PREBUFFER) {
                AL10.alSourcePlay(source);
                started = true;
            }
        } else if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING
                && !queuedBuffers.isEmpty()) {
            // Underran while the network caught up; resume with whatever we have.
            AL10.alSourcePlay(source);
        }

        return true;
    }

    void destroy() {
        if (source != 0) {
            AL10.alSourceStop(source);
            recycleProcessedBuffers();
            for (Integer buffer : queuedBuffers) {
                AL10.alDeleteBuffers(buffer);
            }
            queuedBuffers.clear();
            AL10.alDeleteSources(source);
            source = 0;
        }
        if (scratch != null) {
            MemoryUtil.memFree(scratch);
            scratch = null;
        }
        started = false;
        mode = null;
        sourceChannels = 0;
        sourceRate = 0;
    }

    private boolean create() {
        AL10.alGetError();
        source = AL10.alGenSources();
        if (AL10.alGetError() != AL10.AL_NO_ERROR || source == 0) {
            source = 0;
            if (!allocationFailed) {
                // Latched, because pump() retries every tick and this would otherwise flood the log.
                allocationFailed = true;
                MusicBox.LOGGER.warn("Music Box could not allocate an OpenAL source; too many sounds playing?");
            }
            return false;
        }
        allocationFailed = false;

        AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
        // Distance falloff is computed in Java so the curve is identical regardless of which
        // distance model Minecraft has set globally. AL still handles stereo panning from
        // AL_POSITION, which is the part we actually want from it.
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0F);
        AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 0.0F);
        AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, Float.MAX_VALUE);
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE,
                mode == Mode.HEADPHONES ? AL10.AL_TRUE : AL10.AL_FALSE);

        if (scratch == null) {
            scratch = MemoryUtil.memAllocShort(StreamDecoder.CHUNK_FRAMES * 2);
        }
        return true;
    }

    private void applyMix(float gain, double x, double y, double z) {
        AL10.alSourcef(source, AL10.AL_GAIN, Math.max(0.0F, Math.min(1.0F, gain)));
        if (mode == Mode.HEADPHONES) {
            AL10.alSource3f(source, AL10.AL_POSITION, 0.0F, 0.0F, 0.0F);
        } else {
            AL10.alSource3f(source, AL10.AL_POSITION, (float) x, (float) y, (float) z);
        }
    }

    private void queueChunk(short[] chunk, int channels) {
        scratch.clear();
        int format;
        if (mode == Mode.PROXIMITY) {
            format = AL10.AL_FORMAT_MONO16;
            if (channels == 1) {
                scratch.put(chunk);
            } else {
                for (int i = 0; i + 1 < chunk.length; i += 2) {
                    scratch.put((short) ((chunk[i] + chunk[i + 1]) / 2));
                }
            }
        } else {
            format = AL10.AL_FORMAT_STEREO16;
            if (channels == 2) {
                scratch.put(chunk);
            } else {
                for (short sample : chunk) {
                    scratch.put(sample).put(sample);
                }
            }
        }
        scratch.flip();

        int buffer = AL10.alGenBuffers();
        AL10.alBufferData(buffer, format, scratch, sourceRate);
        AL10.alSourceQueueBuffers(source, buffer);
        queuedBuffers.addLast(buffer);
    }

    private void recycleProcessedBuffers() {
        if (source == 0) {
            return;
        }
        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        while (processed-- > 0) {
            int buffer = AL10.alSourceUnqueueBuffers(source);
            queuedBuffers.remove(buffer);
            AL10.alDeleteBuffers(buffer);
        }
    }
}
