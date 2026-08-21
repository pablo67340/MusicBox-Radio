package com.musicbox.station;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.musicbox.MusicBox;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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

    private static List<Station> stations = List.of();
    private static double proximityRange = 24.0D;
    private static int maxConcurrentStreams = 3;
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

    public static Station byIndex(int index) {
        return index >= 0 && index < stations.size() ? stations.get(index) : null;
    }

    public static void load(Path configDir) {
        file = configDir.resolve(FILE_NAME);
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(configDir);
                writeDefaults();
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

        List<Station> parsed = new ArrayList<>();
        if (root.has("stations") && root.get("stations").isJsonObject()) {
            for (Map.Entry<String, com.google.gson.JsonElement> entry : root.getAsJsonObject("stations").entrySet()) {
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

    private static void writeDefaults() throws IOException {
        JsonObject stationBlock = new JsonObject();
        for (Station station : Defaults.stations()) {
            stationBlock.add(station.label(), new JsonPrimitive(station.url()));
        }

        JsonObject root = new JsonObject();
        root.addProperty("_comment", "Add stations as \"Label\": \"https://stream-url\". "
                + "Direct MP3 streams work best; .m3u and .pls playlists are followed automatically.");
        root.addProperty("proximityRange", proximityRange);
        root.addProperty("maxConcurrentStreams", maxConcurrentStreams);
        root.add("stations", stationBlock);

        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
        MusicBox.LOGGER.info("Wrote default station list to {}", file);
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

            defaults.put("SomaFM Groove Salad", "https://ice1.somafm.com/groovesalad-128-mp3");
            defaults.put("SomaFM Drone Zone", "https://ice1.somafm.com/dronezone-128-mp3");
            defaults.put("SomaFM Deep Space One", "https://ice1.somafm.com/deepspaceone-128-mp3");
            defaults.put("SomaFM Space Station", "https://ice1.somafm.com/spacestation-128-mp3");
            defaults.put("SomaFM Secret Agent", "https://ice1.somafm.com/secretagent-128-mp3");
            defaults.put("SomaFM Underground 80s", "https://ice1.somafm.com/u80s-128-mp3");
            defaults.put("SomaFM Indie Pop Rocks", "https://ice1.somafm.com/indiepop-128-mp3");
            defaults.put("SomaFM Metal Detector", "https://ice1.somafm.com/metal-128-mp3");
            defaults.put("SomaFM DEF CON Radio", "https://ice1.somafm.com/defcon-128-mp3");
            defaults.put("SomaFM Beat Blender", "https://ice1.somafm.com/beatblender-128-mp3");
            defaults.put("SomaFM Boot Liquor", "https://ice1.somafm.com/bootliquor-128-mp3");
            defaults.put("SomaFM Lush", "https://ice1.somafm.com/lush-128-mp3");

            List<Station> list = new ArrayList<>(defaults.size());
            defaults.forEach((label, url) -> list.add(Station.of(label, url)));
            return list;
        }
    }
}
