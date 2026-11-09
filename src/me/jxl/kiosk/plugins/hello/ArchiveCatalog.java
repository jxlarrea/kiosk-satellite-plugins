// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Music from the Internet Archive's netlabel collection, where every release carries a Creative Commons license. No
 * account or API key: the advanced search lists releases, the metadata API lists a release's MP3 files and
 * /services/img/ draws its cover. The JSON reading is apart from the pure mapping, which tests cover on the JVM.
 */
final class ArchiveCatalog {
    static final String ALBUM = "album:";
    static final String TRACK = "track:";
    static final String FOLDER = "folder:";
    static final int PAGE = 50;
    /** Where a long list stops, deep enough for anyone scrolling a kiosk. */
    static final int LIMIT = 500;
    private static final String USER_AGENT = "KioskSatelliteHelloWorld/1.1 (plugin example)";
    private static final String NETLABELS = "collection:netlabels AND format:\"VBR MP3\"";

    /** Well tagged releases to start from, each under a Creative Commons license. */
    static final List<String> FEATURED = Collections.unmodifiableList(Arrays.asList(
        "NS050", "DWK123", "DWK149", "DWK031", "MIXG032", "stqk011", "kzz002", "foot149"));

    /** A release: its item map and its tracks in play order. */
    static final class Album {
        final Map<String, Object> item;
        final List<ArchiveQueue.Track> tracks;

        Album(Map<String, Object> item, List<ArchiveQueue.Track> tracks) { this.item = item; this.tracks = tracks; }
    }

    /** A page of releases and whether more follow. */
    static final class Page {
        final List<Map<String, Object>> items;
        final boolean more;

        Page(List<Map<String, Object>> items, boolean more) { this.items = items; this.more = more; }
    }

    // ── Network ─────────────────────────────────────────────────────

    /** The featured releases, in the order above. */
    List<Map<String, Object>> featured() throws IOException {
        StringBuilder query = new StringBuilder("identifier:(");
        for (int i = 0; i < FEATURED.size(); i++) query.append(i == 0 ? "" : " OR ").append(FEATURED.get(i));
        Page page = search(query.append(")").toString(), "", 0, FEATURED.size());
        List<Map<String, Object>> ordered = new ArrayList<>(page.items);
        Collections.sort(ordered, Comparator.comparingInt(item -> FEATURED.indexOf(identifier(item))));
        return ordered;
    }

    /** Netlabel releases in an order of the advanced search, such as "downloads desc", from offset. */
    Page releases(String sort, int offset) throws IOException { return search(NETLABELS, sort, offset, PAGE); }

    /** Netlabel releases whose title or artist matches the words of query. */
    Page find(String query, int offset) throws IOException {
        String words = words(query);
        if (words.isEmpty()) return new Page(Collections.<Map<String, Object>>emptyList(), false);
        return search("(title:(" + words + ") OR creator:(" + words + ")) AND " + NETLABELS, "downloads desc", offset, PAGE);
    }

    private Page search(String query, String sort, int offset, int rows) throws IOException {
        StringBuilder url = new StringBuilder("https://archive.org/advancedsearch.php?output=json&fl[]=identifier&fl[]=title&fl[]=creator")
            .append("&q=").append(encode(query))
            .append("&rows=").append(rows)
            .append("&page=").append(offset / rows + 1);
        if (!sort.isEmpty()) url.append("&sort[]=").append(encode(sort));
        String body = get(url.toString());
        try {
            return page(new JSONObject(body).getJSONObject("response"), offset);
        } catch (JSONException error) {
            throw new IOException("The Internet Archive sent an answer this player cannot read", error);
        }
    }

    private static Page page(JSONObject response, int offset) throws JSONException {
        JSONArray docs = response.getJSONArray("docs");
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < docs.length(); i++) {
            JSONObject doc = docs.getJSONObject(i);
            items.add(albumItem(doc.getString("identifier"), plain(doc.opt("title")), plain(doc.opt("creator"))));
        }
        int found = response.optInt("numFound", 0);
        return new Page(items, offset + items.size() < Math.min(found, LIMIT) && !items.isEmpty());
    }

    /** One release and its MP3 tracks. */
    Album album(String identifier) throws IOException {
        String body = get("https://archive.org/metadata/" + encode(identifier));
        try {
            return album(identifier, new JSONObject(body));
        } catch (JSONException error) {
            throw new IOException("The Internet Archive sent an answer this player cannot read", error);
        }
    }

    private static Album album(String identifier, JSONObject root) throws IOException, JSONException {
        JSONObject metadata = root.optJSONObject("metadata");
        if (metadata == null) throw new IOException("The Internet Archive has no release " + identifier);
        String title = plain(metadata.opt("title"));
        String creator = plain(metadata.opt("creator"));
        List<Map<String, Object>> files = new ArrayList<>();
        JSONArray list = root.optJSONArray("files");
        for (int i = 0; list != null && i < list.length(); i++) files.add(map(list.getJSONObject(i)));
        return new Album(albumItem(identifier, title, creator), tracks(identifier, title, creator, files));
    }

    private static String get(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        try {
            int code = connection.getResponseCode();
            if (code != 200) throw new IOException("The Internet Archive answered " + code);
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[16384];
                for (int n; (n = in.read(buffer)) > 0; ) {
                    out.write(buffer, 0, n);
                    if (out.size() > 4 * 1024 * 1024) throw new IOException("The Internet Archive answer is too large");
                }
                return out.toString("UTF-8");
            }
        } finally {
            connection.disconnect();
        }
    }

    private static Map<String, Object> map(JSONObject json) throws JSONException {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Iterator<String> keys = json.keys(); keys.hasNext(); ) {
            String key = keys.next();
            map.put(key, plain(json.opt(key)));
        }
        return map;
    }

    /** A JSON value as text: the first of a list, as the archive gives several artists, and "" for null. */
    private static String plain(Object value) {
        if (value instanceof JSONArray) return ((JSONArray) value).optString(0, "");
        return value == null || value == JSONObject.NULL ? "" : String.valueOf(value).trim();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    // ── Mapping, pure Java ──────────────────────────────────────────

    /** A cover for a release. */
    static String artwork(String identifier) { return "https://archive.org/services/img/" + identifier; }

    /** The direct address of a release's file, which supports range requests, so a seek fetches only what it needs. */
    static String audio(ArchiveQueue.Track track) {
        StringBuilder path = new StringBuilder();
        for (String part : track.file.split("/")) {
            if (path.length() > 0) path.append('/');
            path.append(encode(part).replace("+", "%20"));
        }
        return "https://archive.org/download/" + encode(track.identifier) + "/" + path;
    }

    static Map<String, Object> albumItem(String identifier, String title, String creator) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", ALBUM + identifier);
        item.put("kind", "album");
        item.put("title", title.isEmpty() ? identifier : title);
        item.put("subtitle", creator);
        item.put("artworkUrl", artwork(identifier));
        item.put("browsable", true);
        return item;
    }

    static Map<String, Object> folder(String id, String title) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", FOLDER + id);
        item.put("kind", "folder");
        item.put("title", title);
        item.put("playable", false);
        item.put("browsable", true);
        return item;
    }

    static Map<String, Object> trackItem(ArchiveQueue.Track track) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", track.itemId());
        item.put("kind", "track");
        item.put("title", track.title);
        item.put("subtitle", track.artist);
        item.put("artworkUrl", artwork(track.identifier));
        if (track.durationMs > 0) item.put("durationMs", track.durationMs);
        return item;
    }

    /** The release's VBR MP3 files as tracks, in track number order, then by file name. */
    static List<ArchiveQueue.Track> tracks(String identifier, String album, String creator, List<Map<String, Object>> files) {
        List<Map<String, Object>> mp3 = new ArrayList<>();
        for (Map<String, Object> file : files) if ("VBR MP3".equals(file.get("format"))) mp3.add(file);
        Collections.sort(mp3, Comparator.<Map<String, Object>>comparingInt(file -> number(file.get("track"))).thenComparing(file -> text(file.get("name"))));
        List<ArchiveQueue.Track> tracks = new ArrayList<>();
        for (Map<String, Object> file : mp3) {
            String name = text(file.get("name"));
            String title = text(file.get("title"));
            String artist = text(file.get("artist"));
            tracks.add(new ArchiveQueue.Track(identifier, name, title.isEmpty() ? name.replaceAll("\\.mp3$", "") : title,
                artist.isEmpty() ? creator : artist, album, seconds(text(file.get("length"))) * 1000));
        }
        return tracks;
    }

    /** A track number such as "3" or "3/12". Unnumbered tracks go last. */
    static int number(Object value) {
        String text = text(value);
        int slash = text.indexOf('/');
        try {
            return Integer.parseInt((slash < 0 ? text : text.substring(0, slash)).trim());
        } catch (NumberFormatException none) {
            return Integer.MAX_VALUE;
        }
    }

    /** A length the archive gives as seconds ("256.78") or as minutes and seconds ("4:16"). 0 when unknown. */
    static long seconds(String length) {
        if (length.isEmpty()) return 0;
        try {
            if (!length.contains(":")) return Math.round(Double.parseDouble(length));
            long total = 0;
            for (String part : length.split(":")) total = total * 60 + Math.round(Double.parseDouble(part));
            return total;
        } catch (NumberFormatException none) {
            return 0;
        }
    }

    /** Letters, digits and spaces of a search, so it cannot change the query around it. */
    static String words(String query) {
        return query == null ? "" : query.replaceAll("[^\\p{L}\\p{N} ]", " ").trim().replaceAll(" +", " ");
    }

    static String identifier(Map<String, Object> item) {
        String id = text(item.get("id"));
        return id.startsWith(ALBUM) ? id.substring(ALBUM.length()) : id;
    }

    static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
