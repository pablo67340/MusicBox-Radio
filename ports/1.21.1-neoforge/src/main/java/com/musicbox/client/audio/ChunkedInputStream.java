package com.musicbox.client.audio;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Decodes HTTP {@code Transfer-Encoding: chunked} bodies. */
final class ChunkedInputStream extends InputStream {

    private final InputStream delegate;
    private long remainingInChunk;
    private boolean finished;

    ChunkedInputStream(InputStream delegate) {
        this.delegate = delegate;
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int read = read(one, 0, 1);
        return read == -1 ? -1 : one[0] & 0xFF;
    }

    @Override
    public int read(byte[] target, int offset, int length) throws IOException {
        if (finished) {
            return -1;
        }
        if (remainingInChunk == 0 && !advanceToNextChunk()) {
            return -1;
        }
        int toRead = (int) Math.min(length, remainingInChunk);
        int read = delegate.read(target, offset, toRead);
        if (read == -1) {
            finished = true;
            return -1;
        }
        remainingInChunk -= read;
        return read;
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    private boolean advanceToNextChunk() throws IOException {
        if (remainingInChunk == 0 && !finished) {
            // Every chunk after the first is preceded by the CRLF terminating the previous one.
            skipCrlfIfPresent();
        }
        String header = readLine();
        if (header == null) {
            finished = true;
            return false;
        }
        int extension = header.indexOf(';');
        if (extension >= 0) {
            header = header.substring(0, extension);
        }
        long size;
        try {
            size = Long.parseLong(header.trim(), 16);
        } catch (NumberFormatException e) {
            throw new IOException("Malformed chunk size: " + header);
        }
        if (size == 0) {
            finished = true;
            return false;
        }
        remainingInChunk = size;
        return true;
    }

    private void skipCrlfIfPresent() throws IOException {
        delegate.mark(2);
        int first = delegate.read();
        if (first == '\r') {
            int second = delegate.read();
            if (second != '\n') {
                throw new EOFException("Malformed chunk terminator");
            }
            return;
        }
        delegate.reset();
    }

    private String readLine() throws IOException {
        StringBuilder line = new StringBuilder(16);
        int c;
        while ((c = delegate.read()) != -1) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                line.append((char) (c & 0xFF));
            }
        }
        if (c == -1 && line.length() == 0) {
            return null;
        }
        return new String(line.toString().getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.ISO_8859_1);
    }
}
