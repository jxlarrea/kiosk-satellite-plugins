// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.Collections;
import java.util.Map;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * Opens the kiosk's own screens with host.control: the Open button runs the kioskLink setting with openKioskLink,
 * the way a dashboard button runs a ks:// link, and the Open the next alarm and Close the alarm list actions call
 * openAlarms and hideAlarms. Each outcome shows as the Last link reading.
 */
final class LinkDemo {
    private PluginHost host;
    private String link = "";

    synchronized void start(PluginHost host) { this.host = host; }

    synchronized void configure(Map<String, Object> settings) {
        Object value = settings.get("kioskLink");
        link = value instanceof String ? ((String) value).trim() : "";
    }

    /** The Open button: runs the link and answers on the settings page. */
    synchronized void onEvent(String event, Map<String, Object> payload) {
        Object run = payload.get("run");
        if (!"settings.button".equals(event) || !"openKioskLink".equals(payload.get("id")) || !(run instanceof String)) return;
        String id = (String) run;
        PluginHost current = host;
        if (current == null) return;
        if (link.isEmpty()) {
            current.setupResult(id, "error", "Enter a ks:// link first.");
            return;
        }
        control(link, "openKioskLink", Collections.singletonMap("url", link),
            (ok, error) -> current.setupResult(id, ok ? "success" : "error", ok ? "Done." : error));
    }

    synchronized void openNextAlarm() {
        control("Next alarm", "openAlarms", Collections.singletonMap("alarm", "next"), null);
    }

    synchronized void hideAlarms() {
        control("Close alarms", "hideAlarms", Collections.emptyMap(), null);
    }

    interface Outcome {
        void done(boolean ok, String error);
    }

    private void control(String label, String command, Map<String, Object> arguments, Outcome outcome) {
        PluginHost current = host;
        if (current == null) return;
        try {
            current.executeCommand(command, arguments, (ok, data, error) -> {
                current.publishTextSensor("last_link", "Last link", label + ": " + (ok ? "done" : error));
                if (outcome != null) outcome.done(ok, error);
            });
        } catch (UnsupportedOperationException | LinkageError olderHost) {
            current.publishTextSensor("last_link", "Last link", "This Kiosk Satellite version cannot open kiosk links");
            if (outcome != null) outcome.done(false, "This Kiosk Satellite version cannot open kiosk links");
        }
    }

    synchronized void stop() { host = null; }
}
