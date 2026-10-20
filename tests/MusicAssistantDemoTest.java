// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.*;
import me.jxl.kiosk.plugins.PluginHost;

public final class MusicAssistantDemoTest {
    static final class Host implements PluginHost {
        final Set<String> subscriptions = new HashSet<>();
        final Map<String, String> values = new HashMap<>();
        final Map<String, Map<String, Object>> maSubscriptions = new HashMap<>();
        final List<String> maCommands = new ArrayList<>();
        final List<Map<String, Object>> maArgs = new ArrayList<>();
        final List<PluginHost.CommandCallback> maCallbacks = new ArrayList<>();
        PluginHost.CommandCallback identity;
        String statusError;
        public void subscribe(String event) { subscriptions.add(event); }
        public void executeCommand(String command, Map<String, Object> arguments, PluginHost.CommandCallback callback) {
            assert "getSendspinPlayer".equals(command);
            identity = callback;
        }
        public void publishTextSensor(String key, String name, String value) { assert key.matches("[a-z][a-z0-9_]{0,39}"); assert value == null || value.length() <= 512; values.put(key, value); }
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) {}
        public void status(String message, boolean error) { if (error) statusError = message; }
        public void maCall(String command, Map<String, Object> args, PluginHost.CommandCallback callback) {
            maCommands.add(command); maArgs.add(args); maCallbacks.add(callback);
        }
        public void maSubscribe(String key, Map<String, Object> filter, PluginHost.CommandCallback callback) {
            assert !maSubscriptions.containsKey(key);
            maSubscriptions.put(key, filter);
            callback.onResult(true, null, null);
        }
        public void maUnsubscribe(String key) { maSubscriptions.remove(key); }
    }

    static Map<String, Object> identity(boolean connected, String maPlayerId) {
        Map<String, Object> player = new HashMap<>();
        player.put("connected", connected);
        player.put("maConfigured", true);
        player.put("maPlayerId", maPlayerId);
        return player;
    }

    public static void main(String[] args) {
        Host host = new Host();
        MusicAssistantDemo demo = new MusicAssistantDemo();
        demo.start(host);
        assert host.subscriptions.contains("sendspin.player");
        boolean refused = false;
        try { demo.playPause(); } catch (IllegalStateException expected) { refused = true; }
        assert refused;

        // Not connected yet: nothing to follow.
        host.identity.onResult(true, identity(false, ""), null);
        assert "The Sendspin player is not connected".equals(host.values.get("ma_player"));
        assert host.maSubscriptions.isEmpty() && host.maCommands.isEmpty();

        // Music Assistant names the player: one read, then its updates.
        demo.onEvent("ks.sendspin.player", identity(true, "up_abc"));
        assert "players/get".equals(host.maCommands.get(0));
        assert "up_abc".equals(host.maArgs.get(0).get("player_id"));
        Map<String, Object> filter = host.maSubscriptions.get(MusicAssistantDemo.PLAYER);
        assert Collections.singletonList("player_updated").equals(filter.get("events"));
        assert "up_abc".equals(filter.get("objectId"));
        Map<String, Object> player = new HashMap<>();
        player.put("display_name", "Kitchen");
        player.put("playback_state", "idle");
        player.put("volume_level", 30);
        host.maCallbacks.get(0).onResult(true, player, null);
        assert "Kitchen (idle)".equals(host.values.get("ma_player"));
        assert "30%".equals(host.values.get("ma_volume"));
        player.put("playback_state", "playing");
        Map<String, Object> event = new HashMap<>();
        event.put("type", "event");
        event.put("event", "player_updated");
        event.put("objectId", "up_abc");
        event.put("data", player);
        demo.onEvent("ks.ma.subscription." + MusicAssistantDemo.PLAYER, event);
        assert "Kitchen (playing)".equals(host.values.get("ma_player"));

        // Back after a drop: read again, since updates may have been missed.
        demo.onEvent("ks.ma.subscription." + MusicAssistantDemo.PLAYER, Collections.singletonMap("type", "disconnected"));
        assert "Reconnecting to Music Assistant".equals(host.values.get("ma_player"));
        demo.onEvent("ks.ma.subscription." + MusicAssistantDemo.PLAYER, Collections.singletonMap("type", "reconnected"));
        assert "players/get".equals(host.maCommands.get(1));

        // Play or pause goes to the kiosk's player, an error lands in the status.
        demo.playPause();
        assert "players/cmd/play_pause".equals(host.maCommands.get(2));
        assert "up_abc".equals(host.maArgs.get(2).get("player_id"));
        host.maCallbacks.get(2).onResult(false, null, "22: Insufficient permissions");
        assert "Play or pause failed: 22: Insufficient permissions".equals(host.statusError);

        // A late read for a player no longer followed changes nothing.
        demo.onEvent("ks.sendspin.player", identity(true, ""));
        assert host.maSubscriptions.isEmpty();
        assert "Music Assistant has not listed the player yet".equals(host.values.get("ma_player"));
        host.maCallbacks.get(1).onResult(true, player, null);
        assert "Music Assistant has not listed the player yet".equals(host.values.get("ma_player"));
        demo.stop();
        demo.onEvent("ks.sendspin.player", identity(true, "up_abc"));
        assert host.maSubscriptions.isEmpty();
    }
}
