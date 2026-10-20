// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * The kiosk's own Music Assistant player. host.read names it through getSendspinPlayer and sendspin.player. The ma
 * capability reads it, follows its player_updated events and plays or pauses it, all without a Music Assistant token.
 */
final class MusicAssistantDemo {
    static final String PLAYER = "kioskPlayer";

    private PluginHost host;
    private String playerId = "";

    void start(PluginHost host) {
        this.host = host;
        host.publishTextSensor("ma_player", "Music Assistant player", "Waiting for the kiosk's player");
        host.publishTextSensor("ma_volume", "Music Assistant volume", null);
        host.subscribe("sendspin.player");
        host.executeCommand("getSendspinPlayer", Collections.emptyMap(), (ok, data, error) -> {
            if (ok) identity((Map<?, ?>) data);
            else player("Not available: " + error);
        });
    }

    /** The Play or pause Music Assistant action. */
    void playPause() {
        if (host == null || playerId.isEmpty()) throw new IllegalStateException("The kiosk has no Music Assistant player yet");
        PluginHost current = host;
        host.maCall("players/cmd/play_pause", Collections.singletonMap("player_id", playerId), (ok, data, error) -> {
            // The new state arrives through the player_updated subscription.
            if (!ok) current.status("Play or pause failed: " + error, true);
        });
    }

    void onEvent(String event, Map<String, Object> payload) {
        if (host == null) return;
        if (event.equals("ks.sendspin.player")) identity(payload);
        else if (event.equals("ks.ma.subscription." + PLAYER)) playerEvent(payload);
    }

    /** The kiosk's player changed: it connected, disconnected or Music Assistant named it. */
    private void identity(Map<?, ?> player) {
        if (host == null) return;
        String next = player.get("maPlayerId") instanceof String ? (String) player.get("maPlayerId") : "";
        follow(next);
        if (!next.isEmpty()) return;
        if (!Boolean.TRUE.equals(player.get("maConfigured"))) player("Set up Music Assistant in Settings");
        else if (!Boolean.TRUE.equals(player.get("connected"))) player("The Sendspin player is not connected");
        else player("Music Assistant has not listed the player yet");
    }

    private void follow(String next) {
        if (next.equals(playerId)) return;
        if (!playerId.isEmpty()) host.maUnsubscribe(PLAYER);
        playerId = next;
        host.publishTextSensor("ma_volume", "Music Assistant volume", null);
        if (playerId.isEmpty()) return;
        read();
        // Music Assistant sends every event to every client. The filter keeps this player's updates only.
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("events", Collections.singletonList("player_updated"));
        filter.put("objectId", playerId);
        host.maSubscribe(PLAYER, filter, (ok, data, error) -> {
            if (!ok) player("Not subscribed: " + error);
        });
    }

    /** One read for the current state. Changes follow through player_updated. */
    private void read() {
        String reading = playerId;
        host.maCall("players/get", Collections.singletonMap("player_id", playerId), (ok, data, error) -> {
            if (host == null || !reading.equals(playerId)) return;
            if (ok) show(map(data));
            else player("Read failed: " + error);
        });
    }

    private void playerEvent(Map<String, Object> payload) {
        Object type = payload.get("type");
        if ("event".equals(type)) show(map(payload.get("data")));
        else if ("disconnected".equals(type)) player("Reconnecting to Music Assistant");
        // Updates may have been missed while the connection was down.
        else if ("reconnected".equals(type) && !playerId.isEmpty()) read();
    }

    private void show(Map<?, ?> player) {
        Object name = player.get("display_name") != null ? player.get("display_name") : player.get("name");
        // Music Assistant 2.6 and later call it playback_state, earlier versions state.
        Object state = player.get("playback_state") != null ? player.get("playback_state") : player.get("state");
        player(name + " (" + state + ")");
        Object volume = player.get("volume_level");
        host.publishTextSensor("ma_volume", "Music Assistant volume", volume instanceof Number ? ((Number) volume).intValue() + "%" : null);
    }

    private void player(String text) {
        if (host == null) return;
        host.publishTextSensor("ma_player", "Music Assistant player", text.substring(0, Math.min(512, text.length())));
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map ? (Map<?, ?>) value : Collections.emptyMap();
    }

    void stop() { host = null; playerId = ""; }
}
