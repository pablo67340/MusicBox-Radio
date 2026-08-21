package com.musicbox.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.musicbox.MusicBox;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Per-player playback settings, stored as {@code config/musicboxradio/client.json}. */
public final class ClientConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file;
    private static boolean streamingEnabled = true;
    private static float masterVolume = 1.0F;

    private ClientConfig() {
    }

    public static boolean streamingEnabled() {
        return streamingEnabled;
    }

    public static float masterVolume() {
        return masterVolume;
    }

    public static void setStreamingEnabled(boolean value) {
        streamingEnabled = value;
        save();
    }

    public static void load(Path configDir) {
        file = configDir.resolve("client.json");
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(configDir);
                save();
                return;
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.has("streamingEnabled")) {
                    streamingEnabled = root.get("streamingEnabled").getAsBoolean();
                }
                if (root.has("masterVolume")) {
                    masterVolume = Math.max(0.0F, Math.min(1.0F, root.get("masterVolume").getAsFloat()));
                }
            }
        } catch (Exception e) {
            MusicBox.LOGGER.error("Could not read {} - using defaults", file, e);
        }
    }

    public static void save() {
        if (file == null) {
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "Set streamingEnabled to false to mute all internet radio "
                + "on this client, for example while recording or streaming.");
        root.addProperty("streamingEnabled", streamingEnabled);
        root.addProperty("masterVolume", masterVolume);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (Exception e) {
            MusicBox.LOGGER.error("Could not write {}", file, e);
        }
    }
}
