// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.*;

public final class ArchivePlayerDemoTest {
    static ArchiveQueue.Track track(String file) {
        return new ArchiveQueue.Track("rel", file, file.toUpperCase(Locale.ROOT), "Band", "Album", 1000);
    }

    static List<String> titles(ArchiveQueue queue) {
        List<String> titles = new ArrayList<>();
        for (Object item : (List<?>) queue.toMap().get("items")) titles.add((String) ((Map<?, ?>) item).get("title"));
        return titles;
    }

    static String id(ArchiveQueue queue, int index) {
        return (String) ((Map<?, ?>) ((List<?>) queue.toMap().get("items")).get(index)).get("id");
    }

    static Map<String, Object> file(String name, String format, String track, String title, String length) {
        Map<String, Object> file = new HashMap<>();
        file.put("name", name);
        file.put("format", format);
        if (track != null) file.put("track", track);
        if (title != null) file.put("title", title);
        if (length != null) file.put("length", length);
        return file;
    }

    public static void main(String[] args) {
        // Replace, then queue by mode.
        ArchiveQueue queue = new ArchiveQueue(new Random(1));
        queue.replace(Arrays.asList(track("a"), track("b"), track("c")), 1);
        assert "B".equals(queue.current().title);
        assert !queue.insert(Collections.singletonList(track("n")), "next");
        assert queue.insert(Collections.singletonList(track("w")), "now");
        assert "W".equals(queue.current().title);
        assert !queue.insert(Collections.singletonList(track("z")), "add");
        assert Arrays.asList("A", "B", "W", "N", "C", "Z").equals(titles(queue)) : titles(queue);
        assert ((Integer) queue.toMap().get("current")) == 2;

        // Into an empty queue anything starts playing.
        ArchiveQueue empty = new ArchiveQueue();
        assert empty.insert(Collections.singletonList(track("x")), "add");
        assert "X".equals(empty.current().title);

        // Advance follows repeat: one repeats only a track that ended, all wraps around.
        queue.replace(Arrays.asList(track("a"), track("b")), 1);
        assert !queue.advance(false);
        queue.setRepeat("one");
        assert queue.advance(false) && "B".equals(queue.current().title);
        assert !queue.advance(true);
        queue.setRepeat("all");
        assert queue.advance(true) && "A".equals(queue.current().title);
        assert queue.back() && "A".equals(queue.current().title);
        queue.setRepeat("sideways");
        assert "off".equals(queue.repeat());

        // Shuffle never picks the same track again.
        queue.replace(Arrays.asList(track("a"), track("b"), track("c")), 0);
        queue.setShuffle(true);
        for (int i = 0; i < 20; i++) {
            ArchiveQueue.Track before = queue.current();
            assert queue.advance(true);
            assert queue.current() != before;
        }
        queue.setShuffle(false);

        // Moves keep the playing track current, removals move on from it.
        queue.replace(Arrays.asList(track("a"), track("b"), track("c")), 1);
        assert queue.move(id(queue, 2), 0);
        assert Arrays.asList("C", "A", "B").equals(titles(queue));
        assert "B".equals(queue.current().title);
        assert !queue.remove(id(queue, 0));
        assert "B".equals(queue.current().title);
        assert queue.remove(id(queue, 1));
        assert queue.current() == null;
        assert !queue.jump("missing");
        queue.clear();
        assert queue.isEmpty() && queue.current() == null;

        // Catalog mapping: VBR MP3 files only, by track number, titles and lengths read.
        List<ArchiveQueue.Track> tracks = ArchiveCatalog.tracks("rel", "Album", "Band", Arrays.asList(
            file("02 two.mp3", "VBR MP3", "2/3", "Two", "4:16"),
            file("01 one.mp3", "VBR MP3", "1", "One", "256.78"),
            file("01 one.ogg", "Ogg Vorbis", "1", "One", "256.78"),
            file("bonus.mp3", "VBR MP3", null, null, null)));
        assert tracks.size() == 3;
        assert "One".equals(tracks.get(0).title) && tracks.get(0).durationMs == 257000;
        assert "Two".equals(tracks.get(1).title) && tracks.get(1).durationMs == 256000;
        assert "bonus".equals(tracks.get(2).title) && tracks.get(2).durationMs == 0;
        assert "Band".equals(tracks.get(2).artist);
        assert "track:rel/01 one.mp3".equals(tracks.get(0).itemId());
        assert "https://archive.org/download/rel/01%20one.mp3".equals(ArchiveCatalog.audio(tracks.get(0)));
        assert ArchiveCatalog.number("x") == Integer.MAX_VALUE;
        assert "rock n roll".equals(ArchiveCatalog.words(" rock\"n) roll* ")) : ArchiveCatalog.words(" rock\"n) roll* ");

        Map<String, Object> album = ArchiveCatalog.albumItem("rel", "", "Band");
        assert "album:rel".equals(album.get("id")) && "rel".equals(album.get("title")) && Boolean.TRUE.equals(album.get("browsable"));
        assert "rel".equals(ArchiveCatalog.identifier(album));
        assert Boolean.FALSE.equals(ArchiveCatalog.folder("recent", "Recently added").get("playable"));

        // The state KS gets: idle with nothing queued and no buttons, every action with a track.
        Map<String, Object> idle = ArchivePlayerDemo.state(null, "playing", 0, false, "off");
        assert "idle".equals(idle.get("state")) && ((List<?>) idle.get("actions")).isEmpty();
        Map<String, Object> playing = ArchivePlayerDemo.state(tracks.get(0), "playing", -4, true, "all");
        assert "One".equals(playing.get("title")) && Long.valueOf(0).equals(playing.get("positionMs"));
        assert ArchivePlayerDemo.ACTIONS.equals(playing.get("actions"));
        assert Boolean.TRUE.equals(playing.get("shuffle")) && "all".equals(playing.get("repeat"));
        assert "https://archive.org/services/img/rel".equals(playing.get("artworkUrl"));
        System.out.println("ArchivePlayerDemoTest passed");
    }
}
