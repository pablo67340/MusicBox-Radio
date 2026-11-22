package com.musicbox.client.audio;

import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A hand-rolled HTTP client for Shoutcast/Icecast streams.
 * <p>
 * {@link java.net.HttpURLConnection} cannot be used here: Shoutcast servers answer with an
 * {@code ICY 200 OK} status line instead of {@code HTTP/1.1 200 OK}, which the JDK client
 * rejects outright. Speaking HTTP over a raw socket also gives us direct access to the
 * {@code icy-metaint} header needed to de-interleave "now playing" metadata from the audio.
 */
public final class HttpAudioStream implements Closeable {

    private static final int MAX_REDIRECTS = 5;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final String USER_AGENT = "MusicBox/1.0 (Minecraft)";

    private final Socket socket;
    private final InputStream body;
    private final Map<String, String> headers;
    private final String resolvedUrl;

    private HttpAudioStream(Socket socket, InputStream body, Map<String, String> headers, String resolvedUrl) {
        this.socket = socket;
        this.body = body;
        this.headers = headers;
        this.resolvedUrl = resolvedUrl;
    }

    public InputStream body() {
        return body;
    }

    public String resolvedUrl() {
        return resolvedUrl;
    }

    public String header(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    /** Metadata interval in bytes, or 0 when the server sends no inline metadata. */
    public int icyMetaInt() {
        String raw = header("icy-metaint");
        if (raw == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String stationName() {
        String name = header("icy-name");
        return name == null ? null : name.trim();
    }

    @Override
    public void close() {
        try {
            body.close();
        } catch (IOException ignored) {
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    /**
     * Opens {@code url}, following redirects and transparently stepping through M3U/PLS
     * playlists until an actual audio stream is reached.
     */
    public static HttpAudioStream open(String url) throws IOException {
        return open(url, MAX_REDIRECTS, MAX_REDIRECTS);
    }

    private static HttpAudioStream open(String url, int redirectsLeft, int playlistHopsLeft) throws IOException {
        if (redirectsLeft < 0) {
            throw new IOException("Too many redirects");
        }
        if (playlistHopsLeft < 0) {
            throw new IOException("Playlist nested too deeply");
        }

        URI uri = parse(url);
        String scheme = uri.getScheme() == null ? "http" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean secure = scheme.equals("https");
        if (!secure && !scheme.equals("http")) {
            throw new IOException("Unsupported stream protocol: " + scheme);
        }

        String host = uri.getHost();
        if (host == null) {
            throw new IOException("Stream URL has no host: " + url);
        }
        int port = uri.getPort() > 0 ? uri.getPort() : (secure ? 443 : 80);

        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) {
            path = path + "?" + uri.getRawQuery();
        }

        Socket socket = secure
                ? SSLSocketFactory.getDefault().createSocket()
                : new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        socket.setSoTimeout(READ_TIMEOUT_MS);

        boolean handedOff = false;
        try {
            OutputStream out = socket.getOutputStream();
            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + host + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + "\r\n"
                    + "User-Agent: " + USER_AGENT + "\r\n"
                    + "Accept: */*\r\n"
                    + "Icy-MetaData: 1\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            InputStream raw = new BufferedInputStream(socket.getInputStream(), 16 * 1024);
            String statusLine = readLine(raw);
            if (statusLine == null) {
                throw new IOException("Empty response from " + host);
            }
            int status = parseStatus(statusLine);

            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = readLine(raw)) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                            line.substring(colon + 1).trim());
                }
            }

            if (status >= 300 && status < 400) {
                String location = headers.get("location");
                if (location == null) {
                    throw new IOException("Redirect without Location header (" + status + ")");
                }
                socket.close();
                return open(absolutize(uri, location), redirectsLeft - 1, playlistHopsLeft);
            }
            if (status != 200) {
                throw new IOException("Stream returned HTTP " + status);
            }

            InputStream bodyStream = "chunked".equalsIgnoreCase(headers.get("transfer-encoding"))
                    ? new ChunkedInputStream(raw)
                    : raw;

            String contentType = headers.getOrDefault("content-type", "").toLowerCase(Locale.ROOT);
            if (isPlaylist(contentType, uri.getRawPath())) {
                String next = firstEntryOf(readText(bodyStream));
                socket.close();
                if (next == null) {
                    throw new IOException("Playlist contained no stream URL");
                }
                return open(absolutize(uri, next), MAX_REDIRECTS, playlistHopsLeft - 1);
            }

            handedOff = true;
            return new HttpAudioStream(socket, bodyStream, headers, uri.toString());
        } finally {
            if (!handedOff) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static URI parse(String url) throws IOException {
        String candidate = url.trim();
        if (!candidate.contains("://")) {
            candidate = "http://" + candidate;
        }
        try {
            return new URI(candidate);
        } catch (URISyntaxException e) {
            throw new IOException("Malformed stream URL: " + url, e);
        }
    }

    private static String absolutize(URI base, String location) {
        String trimmed = location.trim();
        if (trimmed.contains("://")) {
            return trimmed;
        }
        return base.resolve(trimmed).toString();
    }

    private static int parseStatus(String statusLine) throws IOException {
        // Shoutcast answers "ICY 200 OK"; everything else answers "HTTP/1.x 200 OK".
        String[] parts = statusLine.split(" ");
        if (parts.length < 2) {
            throw new IOException("Unreadable status line: " + statusLine);
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IOException("Unreadable status line: " + statusLine);
        }
    }

    private static boolean isPlaylist(String contentType, String path) {
        if (contentType.contains("mpegurl") || contentType.contains("scpls") || contentType.contains("pls+xml")) {
            return true;
        }
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".m3u") || lower.endsWith(".m3u8") || lower.endsWith(".pls");
    }

    /** Pulls the first playable entry out of an M3U or PLS body. */
    private static String firstEntryOf(String text) {
        List<String> candidates = new ArrayList<>();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.toLowerCase(Locale.ROOT).startsWith("file")) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    candidates.add(line.substring(eq + 1).trim());
                }
                continue;
            }
            if (line.contains("=") && !line.contains("://")) {
                continue;
            }
            candidates.add(line);
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static String readText(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] scratch = new byte[4096];
        int read;
        // Playlists are tiny; cap the read so a mislabelled audio stream cannot hang us.
        while (buffer.size() < 64 * 1024 && (read = in.read(scratch)) > 0) {
            buffer.write(scratch, 0, read);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                buffer.write(c);
            }
        }
        if (c == -1 && buffer.size() == 0) {
            return null;
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
