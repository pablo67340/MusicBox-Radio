package com.musicbox.client.audio;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * One station's audio pipeline: a single decoding thread feeding every place that station
 * is coming out of, so a box and its speakers stay in step and share one connection.
 */
public final class RadioStream {

    private final String url;
    private final StreamDecoder decoder;
    private final AlStreamSource output = new AlStreamSource();
    private boolean disposed;

    RadioStream(String url) {
        this.url = url;
        this.decoder = new StreamDecoder(url);
        this.decoder.start();
    }

    public String url() {
        return url;
    }

    public boolean isFailed() {
        return decoder.state() == StreamDecoder.State.FAILED || decoder.state() == StreamDecoder.State.ENDED;
    }

    public String failure() {
        return decoder.failure();
    }

    /** Track title reported by the station, or an empty string when it sends none. */
    public String nowPlaying() {
        return decoder.nowPlaying();
    }

    public boolean isBuffering() {
        return output.isBuffering();
    }

    /** Band levels for whatever is coming out of the speakers right now. */
    public SpectrumFeed feed() {
        return output.feed();
    }

    void tick(AlStreamSource.Mode mode, List<AlStreamSource.Target> targets, Vec3 listener) {
        if (disposed || isFailed()) {
            return;
        }
        output.pump(decoder, mode, targets, listener);
    }

    void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        decoder.stop();
        output.destroy();
    }
}
