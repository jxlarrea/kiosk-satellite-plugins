// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * Runs the actions the user chose in the action settings: the Run button runs buttonAction, and each quick
 * action shows in the top bar's More menu. The plugin only names the setting. KS reads the action from the saved
 * settings, so the plugin decides when it runs and the user decides what it does.
 */
final class ActionDemo {
    /** A quick action the menu shows: its label, its icon and the setting path runAction takes. */
    static final class QuickAction {
        final String label;
        final String icon;
        final String path;

        QuickAction(String label, String icon, String path) {
            this.label = label;
            this.icon = icon;
            this.path = path;
        }
    }

    private PluginHost host;
    private volatile List<QuickAction> quickActions = Collections.emptyList();

    synchronized void start(PluginHost host) { this.host = host; }

    void configure(Map<String, Object> settings) {
        quickActions = quickActions(settings.get("quickActions"));
    }

    List<QuickAction> quickActions() { return quickActions; }

    /** The rows with an action chosen, in the user's order. A row's path is its index in the list. */
    static List<QuickAction> quickActions(Object value) {
        List<QuickAction> result = new ArrayList<>();
        if (!(value instanceof List)) return result;
        List<?> rows = (List<?>) value;
        for (int i = 0; i < rows.size(); i++) {
            if (!(rows.get(i) instanceof Map)) continue;
            Map<?, ?> row = (Map<?, ?>) rows.get(i);
            Object action = row.get("action");
            if (!(action instanceof Map) || ((Map<?, ?>) action).isEmpty()) continue;
            Object icon = row.get("icon");
            result.add(new QuickAction(String.valueOf(row.get("label")),
                icon instanceof String && !((String) icon).isEmpty() ? (String) icon : "mdi:play-circle-outline",
                "quickActions." + i + ".action"));
        }
        return result;
    }

    /** The Run button: runs buttonAction and answers on the settings page. */
    synchronized void onEvent(String event, Map<String, Object> payload) {
        Object run = payload.get("run");
        if (!"settings.button".equals(event) || !"runAction".equals(payload.get("id")) || !(run instanceof String)) return;
        String id = (String) run;
        PluginHost current = host;
        if (current == null) return;
        run("Run button", "buttonAction", (ok, error) -> current.setupResult(id, ok ? "success" : "error", ok ? "Done." : error));
    }

    /** Runs the action at [path] and reports how it went as the Last action reading. */
    synchronized void run(String label, String path) {
        run(label, path, null);
    }

    interface Outcome {
        void done(boolean ok, String error);
    }

    private synchronized void run(String label, String path, Outcome outcome) {
        if (host == null) return;
        PluginHost current = host;
        try {
            current.runAction(path, (ok, data, error) -> {
                current.publishTextSensor("last_action", "Last action", label + ": " + (ok ? "done" : error));
                if (outcome != null) outcome.done(ok, error);
            });
        } catch (UnsupportedOperationException | LinkageError olderHost) {
            current.publishTextSensor("last_action", "Last action", "This Kiosk Satellite version cannot run actions");
            if (outcome != null) outcome.done(false, "This Kiosk Satellite version cannot run actions");
        }
    }

    synchronized void stop() { host = null; }
}
