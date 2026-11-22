package com.musicbox.client.audio;

import com.musicbox.MusicBox;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pulls an MP3 stream off the network and turns it into interleaved 16-bit PCM chunks.
 * <p>
 * Runs entirely on its own daemon thread. OpenAL is never touched from here - the render
 * thread drains {@link #poll()} and does all the AL work, because the AL context is only
 * current on that thread.
 */
final class StreamDecoder implements Runnable {

    /** Frames (sample pairs) per emitted chunk; ~93 ms at 44.1 kHz. */
    static final int CHUNK_FRAMES = 4096;

    /** Roughly three seconds of slack before the decoder throttles itself. */
    private static final int MAX_QUEUED_CHUNKS = 32;

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    enum State {
        CONNECTING,
        PLAYING,
        FAILED,
        ENDED
    }

    private final String url;
    private final BlockingQueue<short[]> chunks = new ArrayBlockingQueue<>(MAX_QUEUED_CHUNKS);

    private volatile State state = State.CONNECTING;
    private volatile String failure;
    private volatile String nowPlaying = "";
    private volatile int channels;
    private volatile int sampleRate;
    private volatile boolean running = true;

    private Thread thread;
    private HttpAudioStream connection;

    StreamDecoder(String url) {
        this.url = url;
    }

    void start() {
        thread = new Thread(this, "MusicBox Stream #" + THREAD_COUNTER.incrementAndGet());
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();
    }

    void stop() {
        running = false;
        HttpAudioStream open = connection;
        if (open != null) {
            // Closing the socket is what actually unblocks a thread parked in read().
            open.close();
        }
        if (thread != null) {
            thread.interrupt();
        }
        chunks.clear();
    }

    short[] poll() {
        return chunks.poll();
    }

    int available() {
        return chunks.size();
    }

    State state() {
        return state;
    }

    String failure() {
        return failure;
    }

    String nowPlaying() {
        return nowPlaying;
    }

    int channels() {
        return channels;
    }

    int sampleRate() {
        return sampleRate;
    }

    @Override
    public void run() {
        try {
            HttpAudioStream http = HttpAudioStream.open(url);
            connection = http;

            InputStream audio = http.body();
            int metaInterval = http.icyMetaInt();
            if (metaInterval > 0) {
                audio = new IcyMetadataStream(audio, metaInterval, title -> nowPlaying = title);
            }

            decodeLoop(audio);

            if (running) {
                state = State.ENDED;
            }
        } catch (Throwable t) {
            // Throwable, not Exception: a missing decoder class or an OOM has to surface as a
            // failed stream too, otherwise the thread dies silently and the GUI sits on
            // "Buffering..." forever with nothing to explain it.
            if (running) {
                failure = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                state = State.FAILED;
                MusicBox.LOGGER.warn("Music Box stream failed ({}): {}", url, failure, t);
            }
        } finally {
            HttpAudioStream open = connection;
            if (open != null) {
                open.close();
            }
        }
    }

    private void decodeLoop(InputStream audio) throws InterruptedException {
        Bitstream bitstream = new Bitstream(audio);
        Decoder decoder = new Decoder();

        short[] accumulator = null;
        int filled = 0;
        int consecutiveFrameErrors = 0;

        while (running) {
            Header header;
            SampleBuffer output;
            try {
                header = bitstream.readFrame();
                if (header == null) {
                    break;
                }
                output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                bitstream.closeFrame();
                consecutiveFrameErrors = 0;
            } catch (Exception e) {
                // A handful of corrupt frames is normal when joining a live stream mid-flight.
                if (++consecutiveFrameErrors > 32) {
                    throw new IllegalStateException("Stream is not decodable as MP3", e);
                }
                try {
                    bitstream.closeFrame();
                } catch (Exception ignored) {
                }
                continue;
            }

            int frameChannels = output.getChannelCount();
            int frameRate = output.getSampleFrequency();
            if (frameChannels != channels || frameRate != sampleRate) {
                // Flush whatever is half-built; the render thread rebuilds its AL source
                // when the format changes underneath it.
                accumulator = null;
                filled = 0;
                channels = frameChannels;
                sampleRate = frameRate;
            }
            if (channels < 1 || sampleRate < 1) {
                continue;
            }

            int chunkShorts = CHUNK_FRAMES * channels;
            short[] pcm = output.getBuffer();
            int length = output.getBufferLength();
            int read = 0;

            while (read < length) {
                if (accumulator == null) {
                    accumulator = new short[chunkShorts];
                    filled = 0;
                }
                int copy = Math.min(length - read, chunkShorts - filled);
                System.arraycopy(pcm, read, accumulator, filled, copy);
                filled += copy;
                read += copy;

                if (filled == chunkShorts) {
                    submit(accumulator);
                    accumulator = null;
                    filled = 0;
                    state = State.PLAYING;
                }
            }
        }
    }

    private void submit(short[] chunk) throws InterruptedException {
        // Live streams are paced by the server, so a full queue means the listener stopped
        // draining. Drop the stalest chunk rather than stalling the socket into a timeout.
        if (!chunks.offer(chunk, 2, TimeUnit.SECONDS)) {
            chunks.poll();
            chunks.offer(chunk);
        }
    }
}
