package com.musicbox.station;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.musicbox.MusicBox;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Server-authoritative station list, stored as {@code config/musicboxradio/stations.json}.
 * <p>
 * The station block is written as a plain JSON object so the file reads as a literal list of
 * {@code "Label": "url"} pairs, and entries keep the order they appear in the file.
 */
public final class StationConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "stations.json";

    /** Who is allowed to add a station from the music box GUI. */
    public enum Permission {
        OFF,
        OPS,
        ALL;

        static Permission parse(String raw) {
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                MusicBox.LOGGER.warn("Unknown customStations.permission '{}'; falling back to OPS", raw);
                return OPS;
            }
        }
    }

    /** Where a station added from the GUI ends up. */
    public enum Scope {
        /** Stored on that one music box, and lost with it. */
        BLOCK,
        /** Appended to stations.json, so every box on the server offers it. */
        GLOBAL;

        static Scope parse(String raw) {
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                MusicBox.LOGGER.warn("Unknown customStations.scope '{}'; falling back to BLOCK", raw);
                return BLOCK;
            }
        }
    }

    public static final int MAX_LABEL_LENGTH = 64;
    public static final int MAX_URL_LENGTH = 512;

    private static List<Station> stations = List.of();
    private static double proximityRange = 24.0D;
    private static int maxConcurrentStreams = 3;

    private static Permission permission = Permission.OPS;
    private static Scope scope = Scope.BLOCK;
    private static int maxPerBlock = 16;
    private static List<String> allowedDomains = List.of();

    private static Path file;

    private StationConfig() {
    }

    public static List<Station> stations() {
        return stations;
    }

    public static double proximityRange() {
        return proximityRange;
    }

    public static int maxConcurrentStreams() {
        return maxConcurrentStreams;
    }

    public static Permission permission() {
        return permission;
    }

    public static Scope scope() {
        return scope;
    }

    public static int maxPerBlock() {
        return maxPerBlock;
    }

    public static Station byIndex(int index) {
        return index >= 0 && index < stations.size() ? stations.get(index) : null;
    }

    /**
     * The list a box actually offers: the server's stations first, then that box's own
     * additions. Both sides build it the same way so a button index means the same thing
     * on the client that sent it and the server that resolves it.
     */
    public static List<Station> combined(List<Station> blockStations) {
        if (blockStations.isEmpty()) {
            return stations;
        }
        List<Station> all = new ArrayList<>(stations.size() + blockStations.size());
        all.addAll(stations);
        all.addAll(blockStations);
        return all.size() > MusicBox.MAX_STATIONS ? all.subList(0, MusicBox.MAX_STATIONS) : all;
    }

    /** Resolves a button index against server-side data only, never the client's copy. */
    public static Station resolve(int index, List<Station> blockStations) {
        List<Station> all = combined(blockStations);
        return index >= 0 && index < all.size() ? all.get(index) : null;
    }

    public static boolean mayAddStations(Player player) {
        return switch (permission) {
            case OFF -> false;
            // The host of a single-player world counts as an op here even with cheats off,
            // since there is nobody else to protect and the alternative is a + button that
            // never appears.
            case OPS -> player.hasPermissions(2) || isSingleplayerOwner(player);
            case ALL -> true;
        };
    }

    private static boolean isSingleplayerOwner(Player player) {
        MinecraftServer server = player.getServer();
        return server != null && server.isSingleplayerOwner(player.getGameProfile());
    }

    /**
     * Checks a player-submitted station.
     *
     * @return null when acceptable, otherwise the translation key explaining the rejection
     */
    public static String rejectReason(String label, String url) {
        if (label == null || label.isBlank() || label.length() > MAX_LABEL_LENGTH) {
            return "message.musicboxradio.custom.bad_label";
        }
        if (url == null || url.isBlank() || url.length() > MAX_URL_LENGTH) {
            return "message.musicboxradio.custom.bad_url";
        }

        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            return "message.musicboxradio.custom.bad_url";
        }

        String protocol = uri.getScheme();
        if (protocol == null
                || !(protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https"))) {
            return "message.musicboxradio.custom.bad_scheme";
        }

        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return "message.musicboxradio.custom.bad_url";
        }
        if (!allowedDomains.isEmpty() && !hostAllowed(host)) {
            return "message.musicboxradio.custom.blocked_domain";
        }
        return null;
    }

    private static boolean hostAllowed(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        for (String domain : allowedDomains) {
            if (lower.equals(domain) || lower.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Appends a station to the server-wide list and rewrites the config file.
     *
     * @return false if the list is full or the label is already taken
     */
    public static synchronized boolean addGlobal(Station station) {
        if (stations.size() >= MusicBox.MAX_STATIONS) {
            return false;
        }
        for (Station existing : stations) {
            if (existing.label().equalsIgnoreCase(station.label())) {
                return false;
            }
        }
        List<Station> next = new ArrayList<>(stations);
        next.add(station);
        stations = Collections.unmodifiableList(next);
        try {
            write(stations);
        } catch (IOException e) {
            MusicBox.LOGGER.error("Could not persist new station to {}", file, e);
        }
        return true;
    }

    public static void load(Path configDir) {
        file = configDir.resolve(FILE_NAME);
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(configDir);
                write(Defaults.stations());
            }
            read();
        } catch (Exception e) {
            MusicBox.LOGGER.error("Could not read {} - falling back to built-in stations", file, e);
            stations = Defaults.stations();
        }
        MusicBox.LOGGER.info("Music Box loaded {} radio stations", stations.size());
    }

    private static void read() throws IOException {
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }

        if (root.has("proximityRange")) {
            proximityRange = Math.max(1.0D, root.get("proximityRange").getAsDouble());
        }
        if (root.has("maxConcurrentStreams")) {
            maxConcurrentStreams = Math.max(1, Math.min(8, root.get("maxConcurrentStreams").getAsInt()));
        }
        if (root.has("customStations") && root.get("customStations").isJsonObject()) {
            readCustomSettings(root.getAsJsonObject("customStations"));
        }

        List<Station> parsed = new ArrayList<>();
        if (root.has("stations") && root.get("stations").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("stations").entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) {
                    continue;
                }
                Station station = Station.of(entry.getKey(), entry.getValue().getAsString());
                if (station.isValid()) {
                    parsed.add(station);
                } else {
                    MusicBox.LOGGER.warn("Skipping malformed station entry '{}'", entry.getKey());
                }
            }
        }

        if (parsed.size() > MusicBox.MAX_STATIONS) {
            MusicBox.LOGGER.warn("Station list has {} entries; only the first {} are usable",
                    parsed.size(), MusicBox.MAX_STATIONS);
            parsed = parsed.subList(0, MusicBox.MAX_STATIONS);
        }
        stations = Collections.unmodifiableList(parsed);
    }

    private static void readCustomSettings(JsonObject custom) {
        if (custom.has("permission")) {
            permission = Permission.parse(custom.get("permission").getAsString());
        }
        if (custom.has("scope")) {
            scope = Scope.parse(custom.get("scope").getAsString());
        }
        if (custom.has("maxPerBlock")) {
            maxPerBlock = Math.max(0, Math.min(64, custom.get("maxPerBlock").getAsInt()));
        }
        if (custom.has("allowedDomains") && custom.get("allowedDomains").isJsonArray()) {
            List<String> domains = new ArrayList<>();
            for (JsonElement element : custom.getAsJsonArray("allowedDomains")) {
                if (element.isJsonPrimitive()) {
                    String domain = element.getAsString().trim().toLowerCase(Locale.ROOT);
                    if (!domain.isEmpty()) {
                        domains.add(domain);
                    }
                }
            }
            allowedDomains = List.copyOf(domains);
        }
    }

    private static void write(List<Station> toWrite) throws IOException {
        JsonObject stationBlock = new JsonObject();
        for (Station station : toWrite) {
            stationBlock.add(station.label(), new JsonPrimitive(station.url()));
        }

        JsonArray domains = new JsonArray();
        for (String domain : allowedDomains) {
            domains.add(domain);
        }

        JsonObject custom = new JsonObject();
        custom.addProperty("_comment", "permission: off | ops | all - who may use the + button. "
                + "scope: block (stored on that one box) | global (added to this file for everyone). "
                + "allowedDomains: leave empty to allow any host, or list hosts like \"somafm.com\".");
        custom.addProperty("permission", permission.name().toLowerCase(Locale.ROOT));
        custom.addProperty("scope", scope.name().toLowerCase(Locale.ROOT));
        custom.addProperty("maxPerBlock", maxPerBlock);
        custom.add("allowedDomains", domains);

        JsonObject root = new JsonObject();
        root.addProperty("_comment", "Add stations as \"Label\": \"https://stream-url\". "
                + "Direct MP3 streams work best; .m3u and .pls playlists are followed automatically.");
        root.addProperty("proximityRange", proximityRange);
        root.addProperty("maxConcurrentStreams", maxConcurrentStreams);
        root.add("customStations", custom);
        root.add("stations", stationBlock);

        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }

    /**
     * Built-in stations. The first group is the subset of RUST boombox stations whose stream
     * URLs are publicly documented; the rest are long-running public MP3 streams that are
     * safe to ship as examples.
     */
    private static final class Defaults {
        static List<Station> stations() {
            Map<String, String> defaults = new LinkedHashMap<>();

            defaults.put("Smooth Jazz Florida", "http://server.webnetradio.net:5120/stream");
            defaults.put("Salsa Radio", "http://radio.domiplay.net:2002/;");
            defaults.put("Radio Central", "http://philae.shoutca.st:8459/stream");
            defaults.put("JPopsuki Radio", "http://jpopsuki.fm:8000/stream");
            defaults.put("Sensimedia", "http://equinox.shoutca.st:9878/stream");
            defaults.put("Rude FM", "http://sh-uk.audio-stream.com:8042/;");
            defaults.put("WEFUNK Radio", "http://s-00.wefunkradio.com:81/wefunk64.mp3");
            defaults.put("Real Punk Radio", "http://s2.nexuscast.com:8080/stream");
            defaults.put("World Music Radio", "http://stream.wlmm.dk:8010/wmrmp3");
            defaults.put("Magic Oldies Florida", "http://ais-edge07-live365-dal02.cdnstream.com/a46209");
            defaults.put("Vaporwave Radio", "https://radio.plaza.one/mp3");
            // KX105 also publishes SB00427, but that endpoint is AAC and decodes to garbage.
            defaults.put("KX105 Kawartha Lakes", "https://durhamradio.streamb.live/SB00448");

            // 256 kbps where SomaFM publishes it; the rest top out at 128. Only MP3 endpoints
            // are usable, so the -aac variants SomaFM also lists are deliberately not here.
            defaults.put("SomaFM Groove Salad", "https://ice1.somafm.com/groovesalad-256-mp3");
            defaults.put("SomaFM Drone Zone", "https://ice1.somafm.com/dronezone-256-mp3");
            defaults.put("SomaFM Underground 80s", "https://ice1.somafm.com/u80s-256-mp3");
            defaults.put("SomaFM DEF CON Radio", "https://ice1.somafm.com/defcon-256-mp3");
            defaults.put("SomaFM Vaporwaves", "https://ice1.somafm.com/vaporwaves-128-mp3");
            defaults.put("SomaFM Deep Space One", "https://ice1.somafm.com/deepspaceone-128-mp3");
            defaults.put("SomaFM Space Station", "https://ice1.somafm.com/spacestation-128-mp3");
            defaults.put("SomaFM Secret Agent", "https://ice1.somafm.com/secretagent-128-mp3");
            defaults.put("SomaFM Indie Pop Rocks", "https://ice1.somafm.com/indiepop-128-mp3");
            defaults.put("SomaFM Metal Detector", "https://ice1.somafm.com/metal-128-mp3");
            defaults.put("SomaFM Beat Blender", "https://ice1.somafm.com/beatblender-128-mp3");
            defaults.put("SomaFM Boot Liquor", "https://ice1.somafm.com/bootliquor-128-mp3");
            defaults.put("SomaFM Lush", "https://ice1.somafm.com/lush-128-mp3");

            List<Station> list = new ArrayList<>(defaults.size());
            defaults.forEach((label, url) -> list.add(Station.of(label, url)));
            return list;
        }
    }
}
