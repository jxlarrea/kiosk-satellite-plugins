// SPDX-License-Identifier: Apache-2.0
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import me.jxl.kiosk.plugins.PluginHost;
import me.jxl.kiosk.plugins.hello.HelloWorldPlugin;

public final class HelloWorldTest {
    static final class Host implements PluginHost {
        Map<String, Object> screensaver;
        int screensaverPublications;
        public void publishScreensaverAsset(String key, String title, String entry, Map<String, Object> data) {
            assert "dvd".equals(key) && "DVD Logo".equals(title);
            assert "dvd/index.html".equals(entry); screensaver = new HashMap<>(data); screensaverPublications++;
        }
        String title;
        String message;
        String status;
        boolean visible;
        volatile Double sensor;
        volatile Double samples;
        volatile String summary;
        volatile String text;
        volatile Boolean binary;
        volatile String selection;
        volatile boolean switchOn;
        public void publishSwitch(String key, String name, boolean state) { switchOn = state; }
        Map<String, Object> saved;
        public void publishSensor(String key, String name, Map<String, Object> metadata, Double state) { if ("wave".equals(key)) { sensor = state; assert "%".equals(metadata.get("unit")); }
            else if ("samples".equals(key)) { samples = state; assert Integer.valueOf(0).equals(metadata.get("accuracyDecimals")); } }
        public void publishTextSensor(String key, String name, String state) { if ("status".equals(key)) text = state; else if ("summary".equals(key)) summary = state; else if ("last_key".equals(key)) lastKey = state; }
        volatile String lastKey;
        final java.util.Set<String> subscriptions = new java.util.HashSet<>();
        public void subscribe(String event) { subscriptions.add(event); }
        public void executeCommand(String command, Map<String, Object> arguments, PluginHost.CommandCallback callback) {}
        public void unsubscribe(String event) { subscriptions.remove(event); }
        public void removeTextSensor(String key) { if ("last_key".equals(key)) lastKey = null; }
        final List<String> triggers = new java.util.ArrayList<>();
        public void fireTrigger(String id) { triggers.add(id); }
        public void publishBinarySensor(String key, String name, String deviceClass, Boolean state) { binary = state; }
        public void publishSelect(String key, String name, String[] options, String state) { selection = state; assert options.length == 2; }
        public void saveSettings(Map<String, Object> values) { saved = new HashMap<>(values); }
        volatile int publications;
        volatile Map<String, Object> chart;
        public void showWindow(String title, String message, String button) {
            this.title = title; this.message = message; visible = true;
        }
        public void hideWindow() { visible = false; }
        public void log(String message) {}
        public void status(String message, boolean error) { status = message; }
        public void publishSeries(String key, Map<String, Object> chart) {
            assert "demo".equals(key); this.chart = chart; publications++;
        }
        public void removeSeries(String key) { chart = null; }
        String tileLevel;
        String tileText;
        int tilePublications;
        public void publishStatusTile(String key, String title, String level, String text) {
            assert "demo".equals(key) && "Hello World demo".equals(title);
            tileLevel = level; tileText = text; tilePublications++;
        }
        public void removeStatusTile(String key) { assert "demo".equals(key); tileLevel = null; tileText = null; }
        void awaitPublication(int previous) throws Exception {
            long end = System.nanoTime() + 4_000_000_000L;
            while (publications <= previous && System.nanoTime() < end) Thread.sleep(10);
            assert publications > previous : "Demo sampler did not publish";
        }
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Host host = new Host();
        HelloWorldPlugin plugin = new HelloWorldPlugin();
        Map<String, Object> settings = new HashMap<>();
        settings.put("message", "Testing"); settings.put("showOnStart", true);
        settings.put("showChart", true); settings.put("amplitude", 60); settings.put("chartSize", "Regular"); settings.put("chartType", "Line");
        settings.put("pattern", "Sine"); settings.put("seriesColor", "#1976D2");
        settings.put("showKeys", true); settings.put("showStatusTile", true); settings.put("statusLevel", "Healthy"); settings.put("statusText", "Simulated data flowing");
        try {
            plugin.start(host, settings);
            assert host.visible && "Testing".equals(host.message);
            assert "on".equals(host.tileLevel) && "Simulated data flowing".equals(host.tileText) && host.tilePublications == 1;
            plugin.configure(settings);
            assert host.tilePublications == 1 : "Unchanged settings republished the status tile";
            settings.put("statusLevel", "Attention"); plugin.configure(settings);
            assert "warn".equals(host.tileLevel) && host.tilePublications == 2;
            settings.put("statusLevel", "Problem"); settings.put("statusText", " " + new String(new char[90]).replace("\0", "x") + " "); plugin.configure(settings);
            assert "off".equals(host.tileLevel) && host.tileText.length() == 80 && host.tilePublications == 3;
            settings.put("statusLevel", "Muted"); plugin.configure(settings);
            assert "".equals(host.tileLevel);
            settings.put("showStatusTile", false); plugin.configure(settings);
            assert host.tileLevel == null && host.tilePublications == 4;
            settings.put("showKeys", true); settings.put("showStatusTile", true); settings.put("statusLevel", "Healthy"); settings.put("statusText", "Simulated data flowing"); plugin.configure(settings);
            assert "on".equals(host.tileLevel) && host.tilePublications == 5;
            assert host.screensaver.get("logoColor").equals("#00D4FF");
            settings.put("dvdLogoColor", "#112233"); settings.put("dvdBackgroundColor", "#223344");
            plugin.configure(settings);
            assert host.screensaver.get("logoColor").equals("#112233") && host.screensaver.get("backgroundColor").equals("#223344");
            int renderUpdates = host.screensaverPublications;
            plugin.configure(settings);
            assert host.screensaverPublications == renderUpdates : "Unchanged settings restarted the renderer";
            assert host.status.contains("simulated");
            assert "line".equals(host.chart.get("type"));
            assert host.switchOn;
            assert host.samples == 40;
            assert host.summary.equals("Pattern: Sine\nHistory: up to 120 samples\nInterval: 2 seconds");
            plugin.onEvent("switch.chart", Collections.singletonMap("on", false));
            assert !host.switchOn && !host.binary && host.chart == null && host.sensor == null;
            assert Boolean.FALSE.equals(host.saved.get("showChart"));
            assert host.saved.get("message").equals(settings.get("message"));
            try { plugin.onEvent("switch.chart", Collections.singletonMap("on", "false")); throw new AssertionError("Invalid switch accepted"); }
            catch (IllegalArgumentException expected) {}
            assert !host.switchOn;
            plugin.onEvent("switch.chart", Collections.singletonMap("on", true));
            assert host.switchOn && Boolean.TRUE.equals(host.saved.get("showChart"));
            host.awaitPublication(host.publications);
            assert host.sensor != null && host.binary && "Chart running".equals(host.text);
            assert "Sine".equals(host.selection);
            plugin.onEvent("select.pattern", Collections.singletonMap("option", "Triangle"));
            assert "Triangle".equals(host.selection) && "Triangle".equals(host.saved.get("pattern"));
            assert host.saved.get("message").equals(settings.get("message"));
            try { plugin.onEvent("select.pattern", Collections.singletonMap("option", "Unsafe")); throw new AssertionError("Invalid select accepted"); }
            catch (IllegalArgumentException expected) {}
            assert "Triangle".equals(host.selection);
            assert host.summary.startsWith("Pattern: Triangle\n");
            assert ((List<?>) host.chart.get("timestamps")).size() >= 40;
            assert ((List<?>) host.chart.get("series")).size() == 2;
            assert host.subscriptions.contains("device.key") && "Press a hardware button".equals(host.lastKey);
            Map<String, Object> key = new HashMap<>();
            key.put("key", "VOLUME_UP"); key.put("scanCode", 115); key.put("action", "down"); key.put("repeat", 0);
            plugin.onEvent("ks.device.key", key);
            key.put("repeat", 1);
            plugin.onEvent("ks.device.key", key);
            key.put("action", "up"); key.put("repeat", 0);
            plugin.onEvent("ks.device.key", key);
            assert "VOLUME_UP (scan code 115), 1 press".equals(host.lastKey) : host.lastKey;
            assert Collections.singletonList("hardwareKey").equals(host.triggers) : host.triggers;
            Map<String, Object> noKeys = new HashMap<>(settings); noKeys.put("showKeys", false);
            plugin.configure(noKeys);
            assert !host.subscriptions.contains("device.key") && host.lastKey == null;
            plugin.configure(settings);
            plugin.onEvent("window.action", Collections.emptyMap());
            assert host.message.contains("Greetings: 1");
            settings.put("message", "Updated"); plugin.configure(settings);
            assert host.message.startsWith("Updated");
            plugin.execute("hide", Collections.emptyMap()); assert !host.visible;
            plugin.execute("show", Collections.emptyMap()); assert host.visible;
            settings.put("chartSize", "Mini"); settings.put("chartType", "Bar");
            settings.put("amplitude", 0); settings.put("pattern", "Triangle"); settings.put("seriesColor", "#FF0000");
            plugin.configure(settings);
            int before = host.publications; host.awaitPublication(before);
            assert Boolean.TRUE.equals(host.chart.get("compact"));
            assert "bar".equals(host.chart.get("type"));
            List<Map<String, Object>> series = (List<Map<String, Object>>) host.chart.get("series");
            assert "#FF0000".equals(series.get(0).get("color"));
            for (Map<String, Object> item : series) {
                List<Double> values = (List<Double>) item.get("values");
                assert values.get(values.size() - 1) == 0;
            }
            settings.put("showChart", false); plugin.configure(settings); assert host.chart == null;
            assert host.sensor == null && !host.binary && !host.switchOn && "Chart hidden".equals(host.text);
            before = host.publications;
            Thread.sleep(2100); assert host.publications == before;
            settings.put("showChart", true); plugin.configure(settings);
            host.awaitPublication(before); assert host.chart != null;
        } finally { plugin.stop(); }
        int stopped = host.publications;
        Thread.sleep(2100); assert host.publications == stopped : "Sampler survived stop";
        settings.put("showOnStart", false); host.visible = false;
        try {
            plugin.start(host, settings);
            assert !host.visible;
            assert ((List<?>) host.chart.get("timestamps")).size() == 40;
        } finally { plugin.stop(); }
        System.out.println("Hello World controls, chart updates and lifecycle passed.");
    }
}
