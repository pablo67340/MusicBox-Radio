package com.musicbox.client.audio;

/**
 * Turns raw PCM into a handful of band energies for the block renderers.
 * <p>
 * This is a visualiser, not a measurement tool, so it favours looking right over being
 * exact: bands are spaced roughly logarithmically because that is how hearing works, and
 * the top ones are tilted up to offset the downward slope that nearly all music has.
 * Output is in decibels; {@link SpectrumFeed} decides where the top of the meter sits.
 */
public final class Spectrum {

    /** Bars drawn on a music box, low frequency first. */
    public static final int BANDS = 5;

    /** Samples per analysis window. At 44.1 kHz this is ~23 ms of audio. */
    static final int WINDOW = 1024;

    /** Upper edge of each band in Hz; the last one runs to Nyquist. */
    private static final float[] BAND_EDGES = {150.0F, 500.0F, 1500.0F, 4000.0F, Float.MAX_VALUE};

    /** Decibels of lift per band going up, to offset the natural downward slope of music. */
    private static final float TILT_PER_BAND = 4.0F;

    private static final float[] HANN = new float[WINDOW];

    static {
        for (int i = 0; i < WINDOW; i++) {
            HANN[i] = (float) (0.5D - 0.5D * Math.cos(2.0D * Math.PI * i / (WINDOW - 1)));
        }
    }

    private final float[] real = new float[WINDOW];
    private final float[] imaginary = new float[WINDOW];

    /**
     * Analyses one window starting at {@code offset} frames into an interleaved chunk.
     *
     * @return band energies in decibels, low frequency first. Turning these into bar heights
     *         needs a reference level, which {@link SpectrumFeed} supplies.
     */
    public float[] analyse(short[] pcm, int offset, int channels, int sampleRate) {
        int frames = pcm.length / channels;
        for (int i = 0; i < WINDOW; i++) {
            int frame = offset + i;
            float sample = 0.0F;
            if (frame < frames) {
                int base = frame * channels;
                for (int c = 0; c < channels; c++) {
                    sample += pcm[base + c];
                }
                sample /= channels * 32768.0F;
            }
            real[i] = sample * HANN[i];
            imaginary[i] = 0.0F;
        }

        transform();

        // Divide through by the window size. Without this the numbers scale with WINDOW and a
        // full scale tone lands somewhere near +48 dB, which pins every bar to the top.
        float scale = 1.0F / WINDOW;

        float[] bands = new float[BANDS];
        // Bin 0 is DC and carries no musical information, so start at 1.
        for (int bin = 1; bin < WINDOW / 2; bin++) {
            float frequency = bin * sampleRate / (float) WINDOW;
            float re = real[bin] * scale;
            float im = imaginary[bin] * scale;
            bands[bandFor(frequency)] += re * re + im * im;
        }

        for (int i = 0; i < BANDS; i++) {
            // Total energy in the band, not the average per bin: averaging would hand the wide
            // top band an unfair penalty simply for covering more of the spectrum.
            double db = 10.0D * Math.log10(bands[i] + 1.0E-12D);
            // Music carries far more energy low down, so tilt the top bands up or the meter
            // reads as one fat bass bar and four dead ones.
            bands[i] = (float) (db + i * TILT_PER_BAND);
        }
        return bands;
    }

    private static int bandFor(float frequency) {
        for (int i = 0; i < BAND_EDGES.length; i++) {
            if (frequency < BAND_EDGES[i]) {
                return i;
            }
        }
        return BANDS - 1;
    }

    /** In-place iterative radix-2 Cooley-Tukey FFT. WINDOW must be a power of two. */
    private void transform() {
        int n = WINDOW;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                float tr = real[i];
                real[i] = real[j];
                real[j] = tr;
                float ti = imaginary[i];
                imaginary[i] = imaginary[j];
                imaginary[j] = ti;
            }
        }

        for (int length = 2; length <= n; length <<= 1) {
            double angle = -2.0D * Math.PI / length;
            float stepRe = (float) Math.cos(angle);
            float stepIm = (float) Math.sin(angle);
            for (int start = 0; start < n; start += length) {
                float wRe = 1.0F;
                float wIm = 0.0F;
                for (int k = 0; k < length / 2; k++) {
                    int even = start + k;
                    int odd = even + length / 2;
                    float oddRe = real[odd] * wRe - imaginary[odd] * wIm;
                    float oddIm = real[odd] * wIm + imaginary[odd] * wRe;
                    real[odd] = real[even] - oddRe;
                    imaginary[odd] = imaginary[even] - oddIm;
                    real[even] += oddRe;
                    imaginary[even] += oddIm;
                    float nextRe = wRe * stepRe - wIm * stepIm;
                    wIm = wRe * stepIm + wIm * stepRe;
                    wRe = nextRe;
                }
            }
        }
    }
}
