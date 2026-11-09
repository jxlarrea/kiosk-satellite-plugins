// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import me.jxl.kiosk.plugins.PlayerLibrary;
import me.jxl.kiosk.plugins.PlayerListener;
import me.jxl.kiosk.plugins.PlayerQueue;
import me.jxl.kiosk.plugins.PluginHost;
import me.jxl.kiosk.plugins.PluginPlayer;

/**
 * A player under Media Player > Player ("Hello World: Internet Archive") that plays Creative Commons music from the
 * Internet Archive. It shows what a player plugin gets from the player capability: Now Playing with artwork, every
 * transport control, a queue the user can edit and a Music Library source to browse and search. KS calls in on its own
 * threads, so everything that touches the queue or playback runs on one thread here, and network reads on another.
 */
final class ArchivePlayerDemo implements PlayerListener, PlayerLibrary, PlayerQueue, StreamPlayer.Listener {
    static final String KEY = "archive";
    static final List<String> ACTIONS = Collections.unmodifiableList(Arrays.asList(
        "play", "pause", "next", "previous", "seek", "shuffle", "repeat"));

    private final ArchiveCatalog catalog;
    private final ArchiveQueue queue;
    private ExecutorService control;
    private ExecutorService network;
    private PluginHost host;
    private PluginPlayer player;
    private StreamPlayer stream;
    private boolean published;
    /** "idle", "buffering", "playing" or "paused". */
    private String state = "idle";
    private long pausedAtMs;

    ArchivePlayerDemo() { this(new ArchiveCatalog(), new ArchiveQueue()); }

    ArchivePlayerDemo(ArchiveCatalog catalog, ArchiveQueue queue) {
        this.catalog = catalog;
        this.queue = queue;
    }

    synchronized void start(PluginHost host) {
        this.host = host;
        control = Executors.newSingleThreadExecutor(task -> thread(task, "hello-world-player"));
        network = Executors.newFixedThreadPool(2, task -> thread(task, "hello-world-archive-net"));
    }

    /** The Internet Archive player setting: offer the player or take it back. */
    synchronized void configure(Map<String, Object> settings) {
        boolean wanted = !Boolean.FALSE.equals(settings.get("archivePlayer"));
        if (host == null || wanted == published) return;
        if (wanted) host.publishPlayer(KEY, "Internet Archive", this);
        else host.removePlayer(KEY);
        published = wanted;
    }

    void stop() {
        StreamPlayer playing;
        synchronized (this) {
            if (control != null) control.shutdownNow();
            if (network != null) network.shutdownNow();
            control = null;
            network = null;
            playing = stream;
            stream = null;
            player = null;
            host = null;
            published = false;
        }
        // Outside the lock: the decoding thread may be calling back in while it ends.
        if (playing != null) playing.close();
    }

    // ── PlayerListener ──────────────────────────────────────────────

    @Override
    public void onActivate(PluginPlayer player) {
        run(() -> {
            this.player = player;
            stream = new StreamPlayer(player, this);
            // A queue from before the pick moved away stays, paused.
            state = queue.isEmpty() ? "idle" : "paused";
            publish();
        });
    }

    @Override
    public void onDeactivate() {
        run(() -> {
            if (stream != null) {
                if ("playing".equals(state) || "buffering".equals(state)) pausedAtMs = stream.positionMs();
                stream.close();
            }
            stream = null;
            player = null;
            if (!"idle".equals(state)) state = "paused";
        });
    }

    @Override
    public void onCommand(String command, Map<String, Object> arguments) {
        run(() -> {
            switch (command) {
                case "play": resume(); break;
                case "pause": pause(); break;
                case "next": if (queue.advance(true)) playCurrent(0); else stopAtEnd(); break;
                case "previous":
                    // Like most players: back to the start, or to the track before within the first seconds.
                    if (stream != null && stream.positionMs() > 3000) playCurrent(0);
                    else if (queue.back()) playCurrent(0);
                    break;
                case "seek":
                    Object to = arguments.get("positionMs");
                    if (to instanceof Number) seek(((Number) to).longValue());
                    break;
                case "shuffle": queue.setShuffle(Boolean.TRUE.equals(arguments.get("on"))); publish(); break;
                case "repeat": queue.setRepeat(String.valueOf(arguments.get("mode"))); publish(); break;
                default: break;
            }
        });
    }

    // ── StreamPlayer.Listener, on the decoding thread ───────────────

    @Override
    public void onStarted(long positionMs) {
        run(() -> {
            if ("buffering".equals(state)) {
                state = "playing";
                publish(positionMs);
            }
        });
    }

    @Override
    public void onEnded() {
        run(() -> {
            if (queue.advance(false)) playCurrent(0);
            else stopAtEnd();
        });
    }

    @Override
    public void onError(String message) {
        run(() -> {
            PluginHost current = host;
            if (current != null) current.status("Could not play the track: " + message, true);
            // Play tries again from where it stopped.
            if (stream != null) pausedAtMs = stream.positionMs();
            state = "paused";
            publish();
        });
    }

    // ── PlayerLibrary ───────────────────────────────────────────────

    @Override
    public boolean hasDiscover() { return true; }

    @Override
    public boolean hasSearch() { return true; }

    @Override
    public void discover(PluginHost.CommandCallback callback) {
        fetch(callback, () -> Arrays.asList(
            shelf("Featured", catalog.featured()),
            shelf("Most downloaded", first(catalog.releases("downloads desc", 0).items, 12))));
    }

    @Override
    public void browse(String folderId, int offset, PluginHost.CommandCallback callback) {
        fetch(callback, () -> {
            if (folderId == null) {
                return page(Arrays.asList(
                    ArchiveCatalog.folder("featured", "Featured"),
                    ArchiveCatalog.folder("popular", "Most downloaded"),
                    ArchiveCatalog.folder("recent", "Recently added")), false);
            }
            if (folderId.equals(ArchiveCatalog.FOLDER + "featured")) {
                return page(offset == 0 ? catalog.featured() : Collections.<Map<String, Object>>emptyList(), false);
            }
            if (folderId.startsWith(ArchiveCatalog.ALBUM)) {
                return page(trackItems(catalog.album(folderId.substring(ArchiveCatalog.ALBUM.length())).tracks), false);
            }
            String sort = folderId.equals(ArchiveCatalog.FOLDER + "recent") ? "addeddate desc" : "downloads desc";
            ArchiveCatalog.Page releases = catalog.releases(sort, offset);
            return page(releases.items, releases.more);
        });
    }

    @Override
    public void search(String query, PluginHost.CommandCallback callback) {
        fetch(callback, () -> Collections.singletonList(shelf("Releases", catalog.find(query, 0).items)));
    }

    @Override
    public void open(String itemId, PluginHost.CommandCallback callback) {
        fetch(callback, () -> {
            if (!itemId.startsWith(ArchiveCatalog.ALBUM)) throw new IllegalArgumentException("Only releases open");
            ArchiveCatalog.Album album = catalog.album(itemId.substring(ArchiveCatalog.ALBUM.length()));
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("item", album.item);
            detail.put("items", trackItems(album.tracks));
            return detail;
        });
    }

    @Override
    public void play(String itemId, String mode, String withinId, PluginHost.CommandCallback callback) {
        fetch(callback, () -> {
            List<ArchiveQueue.Track> tracks;
            int start = 0;
            if (itemId.startsWith(ArchiveCatalog.ALBUM)) {
                tracks = catalog.album(itemId.substring(ArchiveCatalog.ALBUM.length())).tracks;
            } else if (itemId.startsWith(ArchiveCatalog.TRACK)) {
                String rest = itemId.substring(ArchiveCatalog.TRACK.length());
                int slash = rest.indexOf('/');
                if (slash <= 0) throw new IllegalArgumentException("Unknown track");
                List<ArchiveQueue.Track> album = catalog.album(rest.substring(0, slash)).tracks;
                start = -1;
                for (int i = 0; i < album.size(); i++) if (album.get(i).itemId().equals(itemId)) start = i;
                if (start < 0) throw new IllegalArgumentException("The release no longer has that track");
                // Tapped in its album, the rest of the album follows a replace. Queued alone otherwise.
                boolean whole = withinId != null && "replace".equals(mode);
                tracks = whole ? album : Collections.singletonList(album.get(start));
                if (!whole) start = 0;
            } else {
                throw new IllegalArgumentException("Folders do not play");
            }
            if (tracks.isEmpty()) throw new IllegalStateException("The release has no MP3 tracks");
            List<ArchiveQueue.Track> chosen = tracks;
            int first = start;
            run(() -> enqueue(chosen, first, mode));
            return null;
        });
    }

    // ── PlayerQueue ─────────────────────────────────────────────────

    @Override
    public void queue(PluginHost.CommandCallback callback) { callback.onResult(true, queue.toMap(), null); }

    @Override
    public void playItem(String id) { run(() -> { if (queue.jump(id)) playCurrent(0); }); }

    @Override
    public void moveItem(String id, int toIndex) { run(() -> { if (queue.move(id, toIndex)) queueChanged(); }); }

    @Override
    public void removeItem(String id) {
        run(() -> {
            boolean wasCurrent = queue.remove(id);
            queueChanged();
            if (!wasCurrent) return;
            if (queue.current() == null) stopAtEnd();
            else if ("playing".equals(state) || "buffering".equals(state)) playCurrent(0);
            else { pausedAtMs = 0; publish(); }
        });
    }

    @Override
    public void clear() {
        run(() -> {
            queue.clear();
            if (stream != null) stream.stop();
            state = "idle";
            pausedAtMs = 0;
            queueChanged();
            publish();
        });
    }

    // ── Playback, on the control thread ─────────────────────────────

    private void enqueue(List<ArchiveQueue.Track> tracks, int start, String mode) {
        boolean starts;
        if ("replace".equals(mode)) {
            queue.replace(tracks, start);
            starts = true;
        } else {
            starts = queue.insert(tracks, mode);
        }
        queueChanged();
        if (starts) playCurrent(0);
        else publish();
    }

    private void playCurrent(long positionMs) {
        ArchiveQueue.Track track = queue.current();
        if (track == null || stream == null) {
            // Not the pick right now: the track waits, paused, for the next activation.
            state = track == null ? "idle" : "paused";
            pausedAtMs = positionMs;
            publish();
            return;
        }
        state = "buffering";
        pausedAtMs = positionMs;
        publish(positionMs);
        queueChanged();
        stream.play(ArchiveCatalog.audio(track), positionMs);
    }

    private void resume() {
        if (queue.current() == null) return;
        if ("paused".equals(state) || "idle".equals(state)) playCurrent(pausedAtMs);
    }

    private void pause() {
        if (stream == null || !("playing".equals(state) || "buffering".equals(state))) return;
        pausedAtMs = stream.positionMs();
        stream.stop();
        state = "paused";
        publish();
    }

    private void seek(long positionMs) {
        if (queue.current() == null) return;
        if ("paused".equals(state)) {
            pausedAtMs = positionMs;
            publish();
        } else {
            playCurrent(positionMs);
        }
    }

    private void stopAtEnd() {
        if (stream != null) stream.stop();
        state = queue.isEmpty() ? "idle" : "paused";
        pausedAtMs = 0;
        publish();
    }

    private void publish() {
        publish("playing".equals(state) && stream != null ? stream.positionMs() : pausedAtMs);
    }

    private void publish(long positionMs) {
        PluginPlayer current = player;
        if (current == null || !current.isActive()) return;
        current.update(state(queue.current(), state, positionMs, queue.shuffle(), queue.repeat()));
    }

    /** A player state for KS: the track, where it is and what the buttons may do. */
    static Map<String, Object> state(ArchiveQueue.Track track, String state, long positionMs, boolean shuffle, String repeat) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("state", track == null ? "idle" : state);
        if (track != null) {
            value.put("title", track.title);
            value.put("artist", track.artist);
            value.put("album", track.album);
            value.put("artworkUrl", ArchiveCatalog.artwork(track.identifier));
            if (track.durationMs > 0) value.put("durationMs", track.durationMs);
            value.put("positionMs", Math.max(0, positionMs));
            value.put("actions", ACTIONS);
        } else {
            value.put("actions", Collections.emptyList());
        }
        value.put("shuffle", shuffle);
        value.put("repeat", repeat);
        return value;
    }

    private void queueChanged() {
        PluginPlayer current = player;
        if (current != null && current.isActive()) current.queueChanged();
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private interface Fetch { Object get() throws Exception; }

    /** Answer a library call from the network thread, with the error to show when it fails. */
    private void fetch(PluginHost.CommandCallback callback, Fetch fetch) {
        ExecutorService pool;
        synchronized (this) { pool = network; }
        if (pool == null || pool.isShutdown()) { callback.onResult(false, null, "The player has stopped"); return; }
        pool.execute(() -> {
            try {
                callback.onResult(true, fetch.get(), null);
            } catch (Exception error) {
                callback.onResult(false, null, error.getMessage() != null ? error.getMessage() : "The Internet Archive did not answer");
            }
        });
    }

    private void run(Runnable task) {
        ExecutorService pool;
        synchronized (this) { pool = control; }
        if (pool != null && !pool.isShutdown()) pool.execute(task);
    }

    private static Map<String, Object> shelf(String title, List<Map<String, Object>> items) {
        Map<String, Object> shelf = new LinkedHashMap<>();
        shelf.put("title", title);
        shelf.put("items", items);
        return shelf;
    }

    private static List<Map<String, Object>> first(List<Map<String, Object>> items, int count) {
        return items.subList(0, Math.min(count, items.size()));
    }

    private static Map<String, Object> page(List<Map<String, Object>> items, boolean more) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("items", items);
        page.put("more", more);
        return page;
    }

    private static List<Map<String, Object>> trackItems(List<ArchiveQueue.Track> tracks) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ArchiveQueue.Track track : tracks) items.add(ArchiveCatalog.trackItem(track));
        return items;
    }

    private static Thread thread(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }
}
