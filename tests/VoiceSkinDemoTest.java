// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.*;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

public final class VoiceSkinDemoTest {
    static final class Host implements PluginHost {
        final Set<String> subscriptions = new HashSet<>();
        final Map<String, CommandCallback> reads = new HashMap<>();
        final Map<String, OverlaySpec> overlays = new HashMap<>();
        boolean older;
        public void executeCommand(String command, Map<String, Object> arguments, CommandCallback callback) {
            assert arguments.isEmpty();
            reads.put(command, callback);
        }
        public void subscribe(String event) { if (older) throw new IllegalArgumentException("Unknown KS event"); subscriptions.add(event); }
        public void showOverlay(String key, OverlaySpec spec, OverlayFactory factory) { assert key.matches("[a-z][a-z0-9_]{0,39}") : key; overlays.put(key, spec); }
        public void hideOverlay(String key) { overlays.remove(key); }
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) {}
        public void status(String message, boolean error) {}
    }

    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> value = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) value.put((String) pairs[i], pairs[i + 1]);
        return value;
    }

    public static void main(String[] args) {
        Host host = new Host();
        VoiceSkinDemo demo = new VoiceSkinDemo();
        demo.start(host);
        assert host.subscriptions.containsAll(Arrays.asList(VoiceSkinDemo.EVENTS));
        assert host.reads.containsKey("getVoiceState") && host.reads.containsKey("getVoiceText") && host.reads.containsKey("getVoiceAppearance");

        // Not picked: the turn is KS's to draw.
        host.reads.get("getVoiceState").onResult(true, map("enabled", true, "state", "idle", "skinSelected", false, "overlay", "hidden"), null);
        demo.onEvent("ks.voice.overlay", map("overlay", "full"));
        assert host.overlays.isEmpty() && demo.drawn() == null;

        // Picked: it draws while the overlay shows full screen, on top and passing touches through.
        demo.onEvent("ks.voice.skin", map("selected", true));
        OverlaySpec spec = host.overlays.get(VoiceSkinDemo.KEY);
        assert spec != null && spec.onTop() && !spec.touchable();
        demo.onEvent("ks.voice.level", map("level", 0.5));
        demo.onEvent("ks.voice.text", map("command", "turn on the lights", "answer", "", "tools", Collections.emptyList()));
        assert "full".equals(demo.drawn());
        // Docked, a bubble at the bottom replaces the full screen look.
        demo.onEvent("ks.voice.overlay", map("overlay", "docked"));
        OverlaySpec docked = host.overlays.get(VoiceSkinDemo.KEY);
        assert docked != null && docked != spec && docked.onTop() && !docked.touchable();
        assert "docked".equals(demo.drawn());
        demo.onEvent("ks.voice.overlay", map("overlay", "full"));
        assert host.overlays.get(VoiceSkinDemo.KEY) != docked;
        demo.onEvent("ks.voice.overlay", map("overlay", "hidden"));
        assert host.overlays.isEmpty();

        // It follows the Appearance settings.
        demo.onEvent("ks.voice.appearance", map("theme", "dark", "background", 0.4, "textScale", 1.5, "reactive", false));
        Object[] appearance = demo.appearance();
        assert "dark".equals(appearance[0]) && Float.valueOf(0.4f).equals(appearance[1]);
        assert Float.valueOf(1.5f).equals(appearance[2]) && Boolean.FALSE.equals(appearance[3]);
        demo.onEvent("ks.voice.appearance", map("theme", "auto", "background", null, "textScale", 1.0, "reactive", true));
        assert demo.appearance()[1] == null && Boolean.TRUE.equals(demo.appearance()[3]);

        // Another skin picked mid turn: it steps aside.
        demo.onEvent("ks.voice.overlay", map("overlay", "full"));
        demo.onEvent("ks.voice.skin", map("selected", false));
        assert host.overlays.isEmpty();

        // A read that lands after the events does not undo them.
        Host late = new Host();
        VoiceSkinDemo second = new VoiceSkinDemo();
        second.start(late);
        second.onEvent("ks.voice.skin", map("selected", true));
        second.onEvent("ks.voice.overlay", map("overlay", "full"));
        late.reads.get("getVoiceState").onResult(true, map("skinSelected", false, "overlay", "hidden"), null);
        assert late.overlays.containsKey(VoiceSkinDemo.KEY);

        // Older KS versions do not know the events.
        Host older = new Host();
        older.older = true;
        VoiceSkinDemo third = new VoiceSkinDemo();
        third.start(older);
        assert older.reads.isEmpty();
        third.onEvent("ks.voice.skin", map("selected", true));
        third.onEvent("ks.voice.overlay", map("overlay", "full"));

        demo.stop();
        demo.onEvent("ks.voice.skin", map("selected", true));
        assert demo.drawn() == null;
        System.out.println("VoiceSkinDemoTest passed");
    }
}
