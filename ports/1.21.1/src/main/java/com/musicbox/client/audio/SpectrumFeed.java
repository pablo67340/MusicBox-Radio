package com.musicbox.client.audio;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Holds the band levels for one stream and hands the renderers whatever is being heard
 * <em>right now</em>.
 * <p>
 * That last part is the whole reason this class exists. Audio is analysed when it is handed
 * to OpenAL, but OpenAL is sitting on the better part of a second of queued buffers, so a
 * naive visualiser runs almost a second ahead of the music. Rather than guess at the delay,
 * the queue here is advanced by OpenAL's own report of how many buffers it has finished, so
 * the frames on the front of the deque are always the buffer currently playing. Wall-clock
 * time is only used to pick a sub-frame within that buffer.
 */
public final class SpectrumFeed {

    /** Analysis windows per queued buffer. */
    static final int FRAMES_PER_BUFFER = StreamDecoder.CHUNK_FRAMES / Spectrum.WINDOW;

    /** How fast a bar rises and falls, per second. Fast up, slow down, like a real meter. */
    private static final float ATTACK = 22.0F;
    private static final float DECAY = 5.5F;

    /** Decibels between an empty bar and a full one. */
    private static final float RANGE_DB = 30.0F;

    /** Quietest level the meter will calibrate to, so silence stays silent. */
    private static final float MIN_CEILING = -70.0F;

    /** How fast the reference level falls when the music gets quieter, in dB per second. */
    private static final float CEILING_DECAY = 6.0F;

    private final Deque<float[]> frames = new ArrayDeque<>();
    private final float[] levels = new float[Spectrum.BANDS];

    /**
     * Loudest band seen recently, in decibels, and the top of the meter's scale.
     * <p>
     * Stations differ by a long way in how hard they are mastered, and absolute decibels off a
     * normalised FFT are not a number worth trusting anyway, so the scale calibrates itself
     * rather than being pinned to a fixed level. It snaps up at once and eases back down, which
     * is what stops a loud passage from clipping every bar flat and a quiet one from looking
     * dead.
     */
    private float ceiling = MIN_CEILING;

    private long bufferStartedAt;
    private long lastSmoothedAt;
    private float subFrameNanos = 23_000_000.0F;
    private boolean playing;

    void configure(int sampleRate) {
        if (sampleRate > 0) {
            subFrameNanos = Spectrum.WINDOW * 1.0E9F / sampleRate;
        }
    }

    /** Adds the analysis for one buffer as it is queued onto the source. */
    synchronized void push(float[][] bufferFrames) {
        for (float[] frame : bufferFrames) {
            frames.addLast(frame);
        }
        // A stall can strand frames here; without a cap they would accumulate forever.
        while (frames.size() > FRAMES_PER_BUFFER * 24) {
            frames.removeFirst();
        }
    }

    synchronized void onStarted(long now) {
        playing = true;
        bufferStartedAt = now;
    }

    /** OpenAL has finished {@code count} buffers, so those frames have now been heard. */
    synchronized void onBuffersProcessed(int count, long now) {
        for (int i = 0; i < count * FRAMES_PER_BUFFER && !frames.isEmpty(); i++) {
            frames.removeFirst();
        }
        if (count > 0) {
            bufferStartedAt = now;
        }
    }

    synchronized void stopped() {
        playing = false;
        frames.clear();
        ceiling = MIN_CEILING;
    }

    /**
     * Smoothed band levels in 0..1, low frequency first.
     * <p>
     * Safe to call several times a frame; smoothing advances on elapsed time, so extra
     * callers see the same values rather than pushing the meter along faster.
     */
    public synchronized float[] levels() {
        long now = System.nanoTime();
        float[] target = currentFrame();

        float delta = lastSmoothedAt == 0L ? 0.0F : (now - lastSmoothedAt) / 1.0E9F;
        lastSmoothedAt = now;
        delta = Math.min(delta, 0.25F);

        float floorDb = trackCeiling(target, delta) - RANGE_DB;

        for (int i = 0; i < levels.length; i++) {
            float want = target == null
                    ? 0.0F
                    : Math.max(0.0F, Math.min(1.0F, (target[i] - floorDb) / RANGE_DB));
            float rate = want > levels[i] ? ATTACK : DECAY;
            levels[i] += (want - levels[i]) * Math.min(1.0F, rate * delta);
        }
        return levels;
    }

    private float trackCeiling(float[] frame, float delta) {
        float peak = MIN_CEILING;
        if (frame != null) {
            for (float band : frame) {
                peak = Math.max(peak, band);
            }
        }
        if (peak > ceiling) {
            ceiling = peak;
        } else {
            ceiling = Math.max(MIN_CEILING, ceiling - CEILING_DECAY * delta);
        }
        return ceiling;
    }

    /** Low-end level in 0..1, for anything that should thump rather than dance. */
    public synchronized float bass() {
        float[] current = levels();
        return Math.max(current[0], current[1] * 0.6F);
    }

    private float[] currentFrame() {
        if (!playing || frames.isEmpty()) {
            return null;
        }
        int index = (int) ((System.nanoTime() - bufferStartedAt) / subFrameNanos);
        index = Math.max(0, Math.min(FRAMES_PER_BUFFER - 1, index));

        int i = 0;
        for (float[] frame : frames) {
            if (i++ == index) {
                return frame;
            }
        }
        return frames.peekFirst();
    }
}
