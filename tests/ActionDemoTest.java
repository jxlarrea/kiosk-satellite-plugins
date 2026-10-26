// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.*;
import me.jxl.kiosk.plugins.PluginHost;

public final class ActionDemoTest {
    static final class Host implements PluginHost {
        final Map<String, String> values = new HashMap<>();
        final List<String> ran = new ArrayList<>();
        final List<String> results = new ArrayList<>();
        boolean older;
        public void runAction(String setting, CommandCallback callback) {
            if (older) throw new UnsupportedOperationException("Action settings are unavailable");
            ran.add(setting);
            if (setting.equals("buttonAction")) callback.onResult(false, null, "No action is chosen");
            else callback.onResult(true, null, null);
        }
        public boolean setupResult(String run, String state, String message) { results.add(run + " " + state + " " + message); return true; }
        public void publishTextSensor(String key, String name, String value) { values.put(key, value); }
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) {}
    }

    static Map<String, Object> row(Object... pairs) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) row.put((String) pairs[i], pairs[i + 1]);
        return Collections.unmodifiableMap(row);
    }

    public static void main(String[] args) {
        Host host = new Host();
        ActionDemo demo = new ActionDemo();
        demo.start(host);
        Map<String, Object> script = row("type", "ha_script", "entityId", "script.doorbell");
        Map<String, Object> settings = new HashMap<>();
        settings.put("quickActions", Arrays.asList(
            row("label", "Doorbell", "icon", "mdi:doorbell", "action", script),
            row("label", "Nothing yet", "icon", "", "action", Collections.emptyMap()),
            row("label", "Now Playing", "icon", "", "action", row("type", "now_playing"))));
        demo.configure(settings);
        // Rows without an action stay out of the menu, and each keeps its own index in the path.
        List<ActionDemo.QuickAction> quick = demo.quickActions();
        assert quick.size() == 2;
        assert quick.get(0).path.equals("quickActions.0.action") && quick.get(0).icon.equals("mdi:doorbell");
        assert quick.get(1).path.equals("quickActions.2.action") && quick.get(1).icon.equals("mdi:play-circle-outline");

        demo.run(quick.get(0).label, quick.get(0).path);
        assert host.ran.equals(Collections.singletonList("quickActions.0.action"));
        assert "Doorbell: done".equals(host.values.get("last_action"));

        // The Run button answers on the settings page with what KS said.
        Map<String, Object> press = new HashMap<>();
        press.put("id", "runAction");
        press.put("run", "r1");
        demo.onEvent("settings.button", press);
        assert host.results.equals(Collections.singletonList("r1 error No action is chosen")) : host.results;
        assert "Run button: No action is chosen".equals(host.values.get("last_action"));
        press.put("id", "setupTest");
        demo.onEvent("settings.button", press);
        assert host.results.size() == 1;

        host.older = true;
        demo.run("Doorbell", "quickActions.0.action");
        assert "This Kiosk Satellite version cannot run actions".equals(host.values.get("last_action"));

        demo.configure(new HashMap<String, Object>());
        assert demo.quickActions().isEmpty();
    }
}
