// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.*;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.PluginHost;

public final class StatsDemoTest {
    static final class Host implements PluginHost {
        final List<String> commands = Collections.synchronizedList(new ArrayList<>());
        final Map<String, Map<String, Object>> charts = new HashMap<>();
        final Map<String, String> runs = new HashMap<>();
        final Map<String, String> texts = new HashMap<>();
        volatile List<Map<String, Object>> choices;
        String screensaver;
        Object override;
        Map<String, Object> stats = new HashMap<>();
        public void publishScreensaverView(String key, String title, OverlayFactory factory) { screensaver = key + ": " + title; }
        public void setSettingChoices(String key, List<Map<String, Object>> list) { assert StatsDemo.CHOICES.equals(key); choices = list; }
        public synchronized void publishSeries(String key, Map<String, Object> chart) { assert key.matches("[a-z][a-z0-9_]{0,39}"); charts.put(key, chart); }
        public synchronized void removeSeries(String key) { charts.remove(key); }
        public boolean setupResult(String run, String state, String message) { runs.put(run, state + ": " + message); return true; }
        public void publishTextSensor(String key, String name, String state) { texts.put(key, state); }
        public void executeCommand(String command, Map<String, Object> arguments, CommandCallback callback) {
            commands.add(command);
            if ("getStats".equals(command)) callback.onResult(true, stats, null);
            else if ("setScreensaverMode".equals(command)) { override = arguments.get("mode"); callback.onResult(true, null, null); }
            else if ("clearScreensaverMode".equals(command)) { override = null; callback.onResult(true, null, null); }
            else if ("getScreensaverMode".equals(command)) {
                Map<String, Object> state = new HashMap<>();
                state.put("mode", override != null ? override : "clock");
                state.put("override", override);
                state.put("modes", Arrays.asList(mode("clock", "Clock"), mode(StatsDemo.MODE, "Device stats (Hello World)")));
                callback.onResult(true, state, null);
            } else callback.onResult(false, null, "Unknown command");
        }
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) {}
        public void status(String message, boolean error) {}
    }

    static Map<String, Object> mode(String id, String label) {
        Map<String, Object> mode = new HashMap<>();
        mode.put("id", id); mode.put("label", label);
        return mode;
    }

    static Map<String, Object> settings(String... readings) {
        List<Object> rows = new ArrayList<>();
        for (String reading : readings) rows.add(Collections.singletonMap("reading", reading));
        Map<String, Object> settings = new HashMap<>();
        settings.put("statsTiles", rows);
        settings.put("statsWarnTemp", 70.0);
        return settings;
    }

    static Map<String, Object> event(Object... pairs) {
        Map<String, Object> payload = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) payload.put((String) pairs[i], pairs[i + 1]);
        return payload;
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        Host host = new Host();
        host.stats.put("cpu", 12.4); host.stats.put("cpuClock", 1804.6); host.stats.put("temp", 47.0);
        host.stats.put("battery", 80); host.stats.put("batteryTemp", null); host.stats.put("charging", true);
        StatsDemo demo = new StatsDemo();
        demo.start(host);
        demo.configure(settings("cpu", "cpuClock", "temp"));
        assert "stats: Device stats".equals(host.screensaver);
        assert "Clock\nFrom Screensaver mode".equals(host.texts.get("screensaver_mode")) : host.texts.get("screensaver_mode");

        // The choices are listed at start, grouped, without values until the page opens.
        assert host.choices.size() == StatsDemo.READINGS.length;
        Map<String, Object> clock = host.choices.get(1);
        assert "cpuClock".equals(clock.get("id")) && "CPU clock".equals(clock.get("label")) && "CPU".equals(clock.get("group"));
        assert "Clock".equals(clock.get("category")) && "mdi:speedometer".equals(clock.get("icon")) && !clock.containsKey("value");

        // Nothing is read while the page is closed and no screensaver shows.
        demo.poll();
        assert !host.commands.contains("getStats");

        // The page opens: the values and charts follow.
        demo.onEvent("settings.page", event("open", true));
        assert host.commands.contains("getStats");
        assert "1805 MHz".equals(host.choices.get(1).get("value"));
        assert "12%".equals(host.choices.get(0).get("value"));
        assert "47.0 °C".equals(host.choices.get(2).get("value"));
        assert "Not available".equals(host.choices.get(4).get("value"));
        synchronized (host) {
            assert host.charts.keySet().equals(new HashSet<>(Arrays.asList("stats_cpu", "stats_cpu_clock", "stats_temp"))) : host.charts.keySet();
            Map<String, Object> series = ((List<Map<String, Object>>) host.charts.get("stats_cpu_clock").get("series")).get(0);
            assert Arrays.asList(1804.6).equals(series.get("values"));
        }

        // A tile taken away takes its chart with it. A reading picked twice and an unknown one are dropped.
        demo.configure(settings("battery", "temp", "temp", "gpu"));
        synchronized (host) {
            assert host.charts.keySet().equals(new HashSet<>(Arrays.asList("stats_battery", "stats_temp"))) : host.charts.keySet();
        }
        assert StatsDemo.tiles(settings("battery", "temp", "temp", "gpu").get("statsTiles")).equals(Arrays.asList("battery", "temp"));

        // Close reads add no sample. Later ones do.
        long now = System.currentTimeMillis() + 60_000;
        demo.received(host.stats, now);
        demo.received(host.stats, now + 500);
        synchronized (host) {
            assert ((List<Object>) host.charts.get("stats_temp").get("timestamps")).size() == 2;
        }

        // Refresh reads again and reports.
        demo.onEvent("settings.choices", event("key", StatsDemo.CHOICES, "run", "r1"));
        assert "success: Listed 5 readings.".equals(host.runs.get("r1")) : host.runs.get("r1");

        // The screensaver actions switch to the device stats screensaver and back.
        demo.useScreensaver();
        assert StatsDemo.MODE.equals(host.override);
        assert "Device stats (Hello World)\nSet by a plugin".equals(host.texts.get("screensaver_mode"));
        demo.restoreScreensaver();
        assert host.override == null && "Clock\nFrom Screensaver mode".equals(host.texts.get("screensaver_mode"));

        // The page closes everywhere: reading stops.
        demo.onEvent("settings.page", event("open", false));
        host.commands.clear();
        demo.poll();
        assert !host.commands.contains("getStats");

        assert "stats_battery_temp".equals(StatsDemo.chartKey("batteryTemp"));
        assert "Not available".equals(StatsDemo.format("cpu", null));
        demo.stop();
        demo.onEvent("settings.page", event("open", true));
        assert host.commands.isEmpty();
    }
}
