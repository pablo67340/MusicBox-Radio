package com.musicbox.client.audio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Splits a Shoutcast stream into pure audio and "now playing" metadata.
 * <p>
 * When a server advertises {@code icy-metaint: N} it injects a metadata block after every
 * N bytes of audio: one length byte (in 16-byte units) followed by that many bytes of
 * {@code StreamTitle='...';} text. Reads from this stream return audio only, and each title
 * change is handed to the supplied listener.
 */
final class IcyMetadataStream extends InputStream {

    private final InputStream delegate;
    private final int metaInterval;
    private final Consumer<String> titleListener;

    private int bytesUntilMetadata;
    private String lastTitle = "";

    IcyMetadataStream(InputStream delegate, int metaInterval, Consumer<String> titleListener) {
        this.delegate = delegate;
        this.metaInterval = metaInterval;
        this.titleListener = titleListener;
        this.bytesUntilMetadata = metaInterval;
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int read = read(one, 0, 1);
        return read == -1 ? -1 : one[0] & 0xFF;
    }

    @Override
    public int read(byte[] target, int offset, int length) throws IOException {
        if (bytesUntilMetadata == 0) {
            consumeMetadataBlock();
            bytesUntilMetadata = metaInterval;
        }
        int read = delegate.read(target, offset, Math.min(length, bytesUntilMetadata));
        if (read > 0) {
            bytesUntilMetadata -= read;
        }
        return read;
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    private void consumeMetadataBlock() throws IOException {
        int lengthUnits = delegate.read();
        if (lengthUnits <= 0) {
            return;
        }
        byte[] block = readFully(lengthUnits * 16);
        String text = new String(block, StandardCharsets.UTF_8);
        String title = extractStreamTitle(text);
        if (title != null && !title.equals(lastTitle)) {
            lastTitle = title;
            titleListener.accept(title);
        }
    }

    private byte[] readFully(int length) throws IOException {
        byte[] block = new byte[length];
        int filled = 0;
        while (filled < length) {
            int read = delegate.read(block, filled, length - filled);
            if (read == -1) {
                break;
            }
            filled += read;
        }
        return block;
    }

    private static String extractStreamTitle(String metadata) {
        int start = metadata.indexOf("StreamTitle='");
        if (start < 0) {
            return null;
        }
        start += "StreamTitle='".length();
        int end = metadata.indexOf("';", start);
        if (end < 0) {
            end = metadata.indexOf('\'', start);
        }
        if (end < 0) {
            return null;
        }
        return metadata.substring(start, end).trim();
    }
}
