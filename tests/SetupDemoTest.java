// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.*;
import me.jxl.kiosk.plugins.PluginHost;

public final class SetupDemoTest {
    static final class Steps implements SetupDemo.Scheduler {
        final List<Runnable> queued = new ArrayList<>();
        public void later(long delayMs, Runnable step) { queued.add(step); }
        public void stop() { queued.clear(); }
        void runAll() { while (!queued.isEmpty()) queued.remove(0).run(); }
    }

    static final class Host implements PluginHost {
        final Map<String, String> runs = new HashMap<>();
        final Set<String> over = new HashSet<>();
        String question;
        String[] choices;
        Map<String, Object> updated;
        boolean olderApp;
        public boolean setupResult(String run, String state, String message) {
            if (over.contains(run)) return false;
            runs.put(run, state + ": " + message);
            if (!"running".equals(state)) over.add(run);
            return true;
        }
        public boolean confirmSetup(String run, String title, String message, String confirmLabel) {
            if (over.contains(run)) return false;
            question = title;
            return true;
        }
        public void setSettingChoices(String key, String[] ids, String[] labels) {
            assert "setupDevice".equals(key) && ids.length == labels.length;
            choices = ids;
        }
        public void updateSettings(Map<String, Object> values) {
            if (olderApp) throw new UnsupportedOperationException("Setup controls are unavailable");
            updated = values;
        }
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) {}
    }

    static Map<String, Object> settings(String address, String device) {
        Map<String, Object> settings = new HashMap<>();
        settings.put("setupAddress", address);
        settings.put("setupDevice", device);
        settings.put("setupDeviceName", "");
        return settings;
    }

    static Map<String, Object> event(Object... pairs) {
        Map<String, Object> payload = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) payload.put((String) pairs[i], pairs[i + 1]);
        return payload;
    }

    public static void main(String[] args) {
        Steps steps = new Steps();
        Host host = new Host();
        SetupDemo demo = new SetupDemo(steps);
        demo.start(host);
        demo.configure(settings("demo.local", ""));
        assert host.updated == null;

        // Test reports progress at once and the outcome later.
        demo.onEvent("settings.button", event("id", "setupTest", "run", "t1"));
        assert "running: Connecting to demo.local".equals(host.runs.get("t1"));
        steps.runAll();
        assert "success: Reached demo.local in 42 ms.".equals(host.runs.get("t1")) : host.runs.get("t1");
        demo.configure(settings("fail.local", ""));
        demo.onEvent("settings.button", event("id", "setupTest", "run", "t2"));
        steps.runAll();
        assert host.runs.get("t2").startsWith("error: Could not reach fail.local");

        // A canceled run does no more work.
        demo.onEvent("settings.button", event("id", "setupTest", "run", "t3"));
        demo.onEvent("settings.cancel", event("run", "t3", "reason", "canceled"));
        steps.runAll();
        assert "running: Connecting to fail.local".equals(host.runs.get("t3"));

        // Pair asks partway through and goes on with the answer.
        demo.configure(settings("demo.local", ""));
        demo.onEvent("settings.button", event("id", "setupPair", "run", "p1"));
        steps.runAll();
        assert "Trust this device?".equals(host.question);
        demo.onEvent("settings.confirm", event("run", "p1", "accepted", true));
        steps.runAll();
        assert "success: Paired with demo.local.".equals(host.runs.get("p1"));
        demo.onEvent("settings.button", event("id", "setupPair", "run", "p2"));
        steps.runAll();
        demo.onEvent("settings.confirm", event("run", "p2", "accepted", false));
        assert host.runs.get("p2").startsWith("error: Not paired");
        // An answer for a run that is not waiting is ignored.
        demo.onEvent("settings.confirm", event("run", "p2", "accepted", true));
        steps.runAll();
        assert host.runs.get("p2").startsWith("error: Not paired");

        // Refresh lists the devices as it finds them.
        demo.onEvent("settings.choices", event("key", "setupDevice", "run", "c1"));
        steps.runAll();
        assert Arrays.equals(new String[] {"lamp-kitchen", "lamp-hall", "strip-desk"}, host.choices);
        assert "success: Found 3 devices.".equals(host.runs.get("c1"));
        // A run the host ended stops when its next report is refused.
        demo.onEvent("settings.choices", event("key", "setupDevice", "run", "c2"));
        host.over.add("c2");
        host.choices = null;
        steps.runAll();
        assert host.choices == null;

        // Picking a device fills in its name, and only that setting.
        demo.configure(settings("demo.local", "lamp-hall"));
        assert Collections.singletonMap("setupDeviceName", "Hallway lamp").equals(host.updated) : host.updated;
        host.updated = null;
        demo.configure(settings("demo.local", "lamp-hall"));
        assert host.updated == null;
        // An older app has no updateSettings. The demo keeps working.
        host.olderApp = true;
        demo.configure(settings("demo.local", "strip-desk"));
        demo.stop();
        // A stopped demo reports nothing.
        demo.onEvent("settings.button", event("id", "setupTest", "run", "t9"));
        steps.runAll();
        assert !host.runs.containsKey("t9");
    }
}
