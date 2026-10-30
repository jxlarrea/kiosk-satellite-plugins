// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.*;
import me.jxl.kiosk.plugins.PluginHost;

public final class LinkDemoTest {
    static final class Host implements PluginHost {
        final Map<String, String> values = new HashMap<>();
        final List<String> ran = new ArrayList<>();
        final List<String> results = new ArrayList<>();
        boolean older;
        public void executeCommand(String command, Map<String, Object> arguments, CommandCallback callback) {
            if (older) throw new UnsupportedOperationException("SDK 1 required");
            ran.add(command + " " + arguments);
            if ("ks://nothing".equals(arguments.get("url"))) callback.onResult(false, null, "Invalid host command arguments");
            else callback.onResult(true, null, null);
        }
        public boolean setupResult(String run, String state, String message) { results.add(run + " " + state + " " + message); return true; }
        public void publishTextSensor(String key, String name, String value) { values.put(key, value); }
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) {}
    }

    static Map<String, Object> press(String id, String run) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", id);
        payload.put("run", run);
        return payload;
    }

    public static void main(String[] args) {
        Host host = new Host();
        LinkDemo demo = new LinkDemo();
        demo.start(host);
        demo.configure(Collections.singletonMap("kioskLink", " ks://alarms/next "));

        // The Open button runs the link and answers on the settings page.
        demo.onEvent("settings.button", press("openKioskLink", "r1"));
        assert host.ran.equals(Collections.singletonList("openKioskLink {url=ks://alarms/next}")) : host.ran;
        assert host.results.equals(Collections.singletonList("r1 success Done.")) : host.results;
        assert "ks://alarms/next: done".equals(host.values.get("last_link"));

        demo.configure(Collections.singletonMap("kioskLink", "ks://nothing"));
        demo.onEvent("settings.button", press("openKioskLink", "r2"));
        assert host.results.get(1).equals("r2 error Invalid host command arguments") : host.results;

        demo.configure(Collections.singletonMap("kioskLink", ""));
        demo.onEvent("settings.button", press("openKioskLink", "r3"));
        assert host.results.get(2).equals("r3 error Enter a ks:// link first.") : host.results;
        assert host.ran.size() == 2;

        // Another plugin's button is not ours.
        demo.onEvent("settings.button", press("runAction", "r4"));
        assert host.results.size() == 3;

        demo.openNextAlarm();
        demo.hideAlarms();
        assert host.ran.subList(2, 4).equals(Arrays.asList("openAlarms {alarm=next}", "hideAlarms {}")) : host.ran;
        assert "Close alarms: done".equals(host.values.get("last_link"));

        host.older = true;
        demo.openNextAlarm();
        assert "This Kiosk Satellite version cannot open kiosk links".equals(host.values.get("last_link"));
    }
}
