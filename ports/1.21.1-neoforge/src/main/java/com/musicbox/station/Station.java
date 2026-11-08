package com.musicbox.station;

/** A single entry in the station list: a display name and the stream URL behind it. */
public record Station(String label, String url) {

    public static Station of(String label, String url) {
        return new Station(label == null ? "" : label.trim(), url == null ? "" : url.trim());
    }

    public boolean isValid() {
        return !label.isEmpty() && !url.isEmpty();
    }
}
