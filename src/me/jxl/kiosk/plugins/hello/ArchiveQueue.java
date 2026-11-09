// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The Internet Archive player's queue: tracks in play order, the playing one, shuffle and repeat. Every entry gets an
 * id of its own, so the same track can be queued twice and Kiosk Satellite still names the one to move or remove. Pure
 * Java, so the rules are tested without a device.
 */
final class ArchiveQueue {
    /** One track of an Internet Archive item. */
    static final class Track {
        final String identifier;
        final String file;
        final String title;
        final String artist;
        final String album;
        final long durationMs;

        Track(String identifier, String file, String title, String artist, String album, long durationMs) {
            this.identifier = identifier;
            this.file = file;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.durationMs = durationMs;
        }

        /** The library item id that names it. */
        String itemId() { return ArchiveCatalog.TRACK + identifier + "/" + file; }
    }

    private static final class Entry {
        final String id;
        final Track track;

        Entry(String id, Track track) { this.id = id; this.track = track; }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Random random;
    private int current = -1;
    private int nextId;
    private boolean shuffle;
    private String repeat = "off";

    ArchiveQueue() { this(new Random()); }

    ArchiveQueue(Random random) { this.random = random; }

    synchronized boolean isEmpty() { return entries.isEmpty(); }

    synchronized int size() { return entries.size(); }

    /** The playing track, or null. */
    synchronized Track current() { return current < 0 ? null : entries.get(current).track; }

    synchronized boolean shuffle() { return shuffle; }

    synchronized String repeat() { return repeat; }

    synchronized void setShuffle(boolean on) { shuffle = on; }

    synchronized void setRepeat(String mode) {
        repeat = "one".equals(mode) || "all".equals(mode) ? mode : "off";
    }

    /** Replace the queue with tracks and make the one at start current. */
    synchronized void replace(List<Track> tracks, int start) {
        entries.clear();
        for (Track track : tracks) entries.add(entry(track));
        current = entries.isEmpty() ? -1 : Math.max(0, Math.min(start, entries.size() - 1));
    }

    /**
     * Queue tracks by mode: "now" plays them after the current one and makes the first current, "next" puts them
     * after the current one, "add" at the end. Returns true when the current track changed, so playback starts it.
     */
    synchronized boolean insert(List<Track> tracks, String mode) {
        if (tracks.isEmpty()) return false;
        boolean empty = current < 0;
        int at = "add".equals(mode) || empty ? entries.size() : current + 1;
        List<Entry> added = new ArrayList<>();
        for (Track track : tracks) added.add(entry(track));
        entries.addAll(at, added);
        if ("now".equals(mode) || empty) {
            current = at;
            return true;
        }
        return false;
    }

    /**
     * Move on after a track. A track that ended by itself repeats under repeat one, while next skips it. Returns false
     * at the end of the queue with repeat off.
     */
    synchronized boolean advance(boolean manual) {
        if (current < 0) return false;
        if (!manual && "one".equals(repeat)) return true;
        if (shuffle && entries.size() > 1) {
            int pick = random.nextInt(entries.size() - 1);
            current = pick >= current ? pick + 1 : pick;
            return true;
        }
        if (current + 1 < entries.size()) {
            current++;
            return true;
        }
        if ("all".equals(repeat)) {
            current = 0;
            return true;
        }
        return false;
    }

    /** Go back a track, staying on the first. */
    synchronized boolean back() {
        if (current < 0) return false;
        if (current > 0) current--;
        return true;
    }

    /** Make the entry with this id current. */
    synchronized boolean jump(String id) {
        int index = indexOf(id);
        if (index < 0) return false;
        current = index;
        return true;
    }

    /** Move the entry with this id so it ends up at toIndex. */
    synchronized boolean move(String id, int toIndex) {
        int from = indexOf(id);
        if (from < 0) return false;
        int to = Math.max(0, Math.min(toIndex, entries.size() - 1));
        Entry playing = current < 0 ? null : entries.get(current);
        entries.add(to, entries.remove(from));
        if (playing != null) current = entries.indexOf(playing);
        return true;
    }

    /** Take out the entry with this id. Returns true when it was the current one, which then moves to the next. */
    synchronized boolean remove(String id) {
        int index = indexOf(id);
        if (index < 0) return false;
        entries.remove(index);
        if (index < current) {
            current--;
            return false;
        }
        if (index != current) return false;
        if (current >= entries.size()) current = entries.isEmpty() ? -1 : ("all".equals(repeat) ? 0 : -1);
        return true;
    }

    synchronized void clear() {
        entries.clear();
        current = -1;
    }

    /** The queue as PlayerQueue.queue answers it. */
    synchronized Map<String, Object> toMap() {
        List<Object> items = new ArrayList<>();
        for (Entry entry : entries) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", entry.id);
            item.put("title", entry.track.title);
            item.put("artist", entry.track.artist);
            item.put("artworkUrl", ArchiveCatalog.artwork(entry.track.identifier));
            if (entry.track.durationMs > 0) item.put("durationMs", entry.track.durationMs);
            items.add(item);
        }
        Map<String, Object> queue = new LinkedHashMap<>();
        queue.put("items", Collections.unmodifiableList(items));
        queue.put("current", current);
        return queue;
    }

    private Entry entry(Track track) { return new Entry("q" + (++nextId), track); }

    private int indexOf(String id) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).id.equals(id)) return i;
        return -1;
    }
}
