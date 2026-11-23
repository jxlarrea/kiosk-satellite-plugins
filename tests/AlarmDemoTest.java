// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.*;
import me.jxl.kiosk.plugins.PluginHost;

public final class AlarmDemoTest {
    static final class Host implements PluginHost {
        final Set<String> subscriptions = new HashSet<>();
        final Map<String, String> values = new HashMap<>();
        final List<CommandCallback> reads = new ArrayList<>();
        final List<String> ran = new ArrayList<>();
        final List<String> results = new ArrayList<>();
        boolean older;
        String refuse;
        public void executeCommand(String command, Map<String, Object> arguments, CommandCallback callback) {
            if ("getAlarmState".equals(command)) { reads.add(callback); return; }
            ran.add(command + " " + arguments);
            if (command.equals(refuse)) callback.onResult(false, null, "no alarm is ringing");
            else callback.onResult(true, null, null);
        }
        public boolean setupResult(String run, String state, String message) { results.add(run + " " + state + " " + message); return true; }
        public void subscribe(String event) { if (older) throw new IllegalArgumentException("Unknown KS event"); subscriptions.add(event); }
        public void unsubscribe(String event) { if (older) throw new IllegalArgumentException("Unknown KS event"); subscriptions.remove(event); }
        public void publishTextSensor(String key, String name, String value) { assert value.length() <= 512; values.put(key, value); }
        public void removeTextSensor(String key) { values.remove(key); }
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) {}
    }

    static Map<String, Object> state(String phase, String label, String at) {
        Map<String, Object> value = new HashMap<>();
        value.put("phase", phase); value.put("label", label); value.put("at", at);
        return value;
    }

    static Map<String, Object> settings(boolean show, boolean say) {
        Map<String, Object> value = new HashMap<>();
        value.put("showAlarms", show); value.put("sayAlarmSnoozes", say);
        return value;
    }

    static Map<String, Object> press(String id, String run) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", id); payload.put("run", run);
        return payload;
    }

    public static void main(String[] args) {
        Host host = new Host();
        AlarmDemo demo = new AlarmDemo();
        demo.start(host);
        demo.configure(settings(true, true));
        assert host.subscriptions.contains("alarm.state");
        assert "Reading".equals(host.values.get("alarm_state"));
        host.reads.get(0).onResult(true, state("idle", "", null), null);
        assert "idle".equals(host.values.get("alarm_state"));

        // Each snooze of a ring counts and is said.
        String seven = "2026-11-24T12:00:00.000Z";
        demo.onEvent("ks.alarm.state", state("ringing", "Work", seven));
        assert "ringing, Work".equals(host.values.get("alarm_state"));
        demo.onEvent("ks.alarm.state", state("snoozed", "Work", seven));
        assert "snoozed, Work, snoozed 1 time".equals(host.values.get("alarm_state"));
        demo.onEvent("ks.alarm.state", state("ringing", "Work", seven));
        demo.onEvent("ks.alarm.state", state("snoozed", "Work", seven));
        assert "snoozed, Work, snoozed 2 times".equals(host.values.get("alarm_state"));
        assert host.ran.equals(Arrays.asList("sayAlarm {text=Snooze 1}", "sayAlarm {text=Snooze 2}")) : host.ran;
        demo.onEvent("ks.alarm.state", state("idle", "", null));
        assert "idle".equals(host.values.get("alarm_state"));

        // The next ring starts its own count, and the count stays quiet with Say off.
        demo.configure(settings(true, false));
        String eight = "2026-11-25T12:00:00.000Z";
        demo.onEvent("ks.alarm.state", state("ringing", "", eight));
        demo.onEvent("ks.alarm.state", state("snoozed", "", eight));
        assert "snoozed, snoozed 1 time".equals(host.values.get("alarm_state"));
        assert host.ran.size() == 2;

        // The buttons run the alarm controls and answer on the settings page.
        host.ran.clear();
        demo.onEvent("settings.button", press("alarmSnooze", "r1"));
        demo.onEvent("settings.button", press("alarmStop", "r2"));
        host.refuse = "skipNextAlarm";
        demo.onEvent("settings.button", press("alarmSkip", "r3"));
        demo.onEvent("settings.button", press("openKioskLink", "r4"));
        assert host.ran.equals(Arrays.asList("snoozeAlarm {}", "stopAlarm {}", "skipNextAlarm {}")) : host.ran;
        assert host.results.equals(Arrays.asList("r1 success Done.", "r2 success Done.", "r3 error no alarm is ringing")) : host.results;

        // A read from before a settings change is stale, and an event wins over an older read.
        demo.configure(settings(false, false));
        assert host.subscriptions.isEmpty() && !host.values.containsKey("alarm_state");
        demo.configure(settings(true, false));
        demo.onEvent("ks.alarm.state", state("sunrise", "Gym", "2026-11-26T11:30:00.000Z"));
        host.reads.get(1).onResult(true, state("idle", "", null), null);
        assert "sunrise, Gym".equals(host.values.get("alarm_state"));

        demo.stop();
        demo.onEvent("ks.alarm.state", state("ringing", "", eight));
        assert "sunrise, Gym".equals(host.values.get("alarm_state"));

        Host older = new Host();
        older.older = true;
        AlarmDemo legacy = new AlarmDemo();
        legacy.start(older);
        legacy.configure(settings(true, false));
        assert older.reads.isEmpty();
        assert "Needs a newer Kiosk Satellite".equals(older.values.get("alarm_state"));
    }
}
