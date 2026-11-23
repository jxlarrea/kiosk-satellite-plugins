// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.Collections;
import java.util.Map;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * Follows this kiosk's own alarms with alarm.state, counts the snoozes of each ring and can say the count with
 * sayAlarm. The Snooze, Stop and Skip buttons call snoozeAlarm, stopAlarm and skipNextAlarm.
 */
final class AlarmDemo {
    private PluginHost host;
    private boolean watching;
    private boolean sayCount;
    private int generation;
    private boolean live;
    private String phase = "idle";
    /** The ring the count is for: alarm.state keeps its at through every snooze. */
    private Object ring;
    private int snoozes;

    synchronized void start(PluginHost host) { this.host = host; }

    synchronized void configure(Map<String, Object> settings) {
        if (host == null) return;
        sayCount = Boolean.TRUE.equals(settings.get("sayAlarmSnoozes"));
        boolean next = Boolean.TRUE.equals(settings.get("showAlarms"));
        if (next == watching) return;
        watching = next;
        generation++;
        live = false;
        if (!watching) {
            try { host.unsubscribe("alarm.state"); } catch (IllegalArgumentException olderHost) {}
            host.removeTextSensor("alarm_state");
            return;
        }
        try {
            host.subscribe("alarm.state");
        } catch (IllegalArgumentException olderHost) {
            host.publishTextSensor("alarm_state", "Alarm state", "Needs a newer Kiosk Satellite");
            return;
        }
        host.publishTextSensor("alarm_state", "Alarm state", "Reading");
        int current = generation;
        host.executeCommand("getAlarmState", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
            synchronized (AlarmDemo.this) {
                // A later event is newer than this read, and a settings change makes it stale.
                if (host == null || current != generation || live) return;
                if (ok && data instanceof Map) show((Map<?, ?>) data, false);
                else host.publishTextSensor("alarm_state", "Alarm state", "Not available");
            }
        });
    }

    synchronized void onEvent(String event, Map<String, Object> payload) {
        if (host == null) return;
        if (watching && "ks.alarm.state".equals(event)) {
            live = true;
            show(payload, true);
            return;
        }
        Object run = payload.get("run");
        if (!"settings.button".equals(event) || !(run instanceof String)) return;
        Object id = payload.get("id");
        String command = "alarmSnooze".equals(id) ? "snoozeAlarm"
            : "alarmStop".equals(id) ? "stopAlarm"
            : "alarmSkip".equals(id) ? "skipNextAlarm"
            : null;
        if (command == null) return;
        PluginHost current = host;
        try {
            current.executeCommand(command, Collections.<String, Object>emptyMap(),
                (ok, data, error) -> current.setupResult((String) run, ok ? "success" : "error", ok ? "Done." : error));
        } catch (UnsupportedOperationException | LinkageError olderHost) {
            current.setupResult((String) run, "error", "Needs a newer Kiosk Satellite");
        }
    }

    /** Shows the state. A snooze from an event counts toward its ring and, when asked, is said. */
    private void show(Map<?, ?> state, boolean event) {
        Object next = state.get("phase");
        String now = next instanceof String ? (String) next : "idle";
        Object at = state.get("at");
        if (at != null && !at.equals(ring)) {
            ring = at;
            snoozes = 0;
        }
        if (event && "snoozed".equals(now) && "ringing".equals(phase)) {
            snoozes++;
            if (sayCount) say("Snooze " + snoozes);
        }
        phase = now;
        StringBuilder value = new StringBuilder(now);
        Object label = state.get("label");
        if (label instanceof String && !((String) label).isEmpty()) value.append(", ").append(label);
        if (snoozes > 0 && !"idle".equals(now)) value.append(", snoozed ").append(snoozes).append(snoozes == 1 ? " time" : " times");
        host.publishTextSensor("alarm_state", "Alarm state", value.substring(0, Math.min(512, value.length())));
    }

    private void say(String text) {
        PluginHost current = host;
        current.executeCommand("sayAlarm", Collections.<String, Object>singletonMap("text", text), (ok, data, error) -> {
            if (!ok) current.log("Could not say \"" + text + "\": " + error);
        });
    }

    synchronized void stop() {
        host = null;
        watching = false;
        live = false;
        generation++;
        phase = "idle";
        ring = null;
        snoozes = 0;
    }
}
