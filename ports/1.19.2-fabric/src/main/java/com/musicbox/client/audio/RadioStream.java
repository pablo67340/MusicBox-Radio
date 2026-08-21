package com.musicbox.client.audio;

/** One music box's audio pipeline: a decoding thread feeding a single OpenAL source. */
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

    void tick(AlStreamSource.Mode mode, float gain, double x, double y, double z) {
        if (disposed || isFailed()) {
            return;
        }
        output.pump(decoder, mode, gain, x, y, z);
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
