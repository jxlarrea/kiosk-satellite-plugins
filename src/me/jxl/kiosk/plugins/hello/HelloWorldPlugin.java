// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.PluginHost;

/** Settings controls including lists, pickers, buttons and plugin choices, a floating window, native overlays, actions, live readings, a read-only chart, an Overview status tile, hardware keys, Voice Satellite state, a voice skin and intercom state. */
public final class HelloWorldPlugin implements KioskPlugin {
    private final HomeAssistantDemo homeAssistant = new HomeAssistantDemo();
    private final StatusTileDemo statusTile = new StatusTileDemo();
    private final KeyDemo keys = new KeyDemo();
    private final VoiceDemo voice = new VoiceDemo();
    private final VoiceSkinDemo voiceSkin = new VoiceSkinDemo();
    private final IntercomDemo intercom = new IntercomDemo();
    private final ListDemo lists = new ListDemo();
    private final OverlayDemo overlays = new OverlayDemo();
    private final SetupDemo setup = new SetupDemo();
    private PluginHost host;
    private final ShizukuDemo shizuku = new ShizukuDemo();
    private Map<String, Object> savedSettings;
    private String message;
    private boolean visible;
    private Map<String, Object> screensaverOptions;
    private int greetings;
    private boolean showChart;
    private boolean compactChart;
    private String chartType;
    private double amplitude;
    private String pattern;
    private String seriesColor;
    private ScheduledExecutorService sampler;
    private final List<Long> times = new ArrayList<>();
    private final List<Double> wave = new ArrayList<>();
    private final List<Double> reference = new ArrayList<>();
    private int phase;

    @Override
    public synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        overlays.start(host);
        voiceSkin.start(host);
        setup.start(host);
        configure(settings);
        shizuku.start(host, settings);
        host.log("Hello World started");
        host.status("Chart readings use simulated data. Shizuku readings come from this device.", false);
        if (Boolean.TRUE.equals(settings.get("showOnStart"))) show();
        // Seed a short simulated history so the chart is useful immediately.
        long now = System.currentTimeMillis();
        for (int i = 39; i >= 0; i--) sample(now - i * 2000L);
        if (showChart) publishChart();
        publishEntities();
        sampler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "hello-world-demo");
            thread.setDaemon(true);
            return thread;
        });
        sampler.scheduleWithFixedDelay(this::tick, 2, 2, TimeUnit.SECONDS);
    }

    @Override
    public synchronized void configure(Map<String, Object> settings) {
        savedSettings = new LinkedHashMap<>(settings);
        homeAssistant.configure(host, settings);
        message = (String) settings.get("message");
        boolean nextChart = Boolean.TRUE.equals(settings.get("showChart"));
        if (showChart && !nextChart) host.removeSeries("demo");
        showChart = nextChart;
        chartType = "Bar".equals(settings.get("chartType")) ? "bar" : "line";
        compactChart = "Mini".equals(settings.get("chartSize"));
        amplitude = ((Number) settings.get("amplitude")).doubleValue();
        pattern = (String) settings.get("pattern");
        seriesColor = (String) settings.get("seriesColor");
        shizuku.configure(settings);
        statusTile.configure(host, settings);
        keys.configure(host, settings);
        voice.configure(host, settings);
        intercom.configure(host, settings);
        lists.configure(host, settings);
        setup.configure(settings);
        Map<String, Object> nextScreensaver = DvdScreensaver.options(settings);
        if (!nextScreensaver.equals(screensaverOptions)) {
            host.publishScreensaverAsset("dvd", "DVD Logo", "dvd/index.html", nextScreensaver);
            screensaverOptions = nextScreensaver;
        }
        // The next tick publishes chart edits without creating extra update bursts.
        if (visible) show();
        publishEntities();
    }

    private synchronized void tick() {
        if (host == null) return;
        shizuku.tick();
        if (!showChart) return;
        try {
            long now = System.currentTimeMillis();
            if (!times.isEmpty() && now <= times.get(times.size() - 1)) return;
            sample(now);
            publishChart();
            publishEntities();
        } catch (RuntimeException error) {
            host.status("Could not update the demo chart: " + error.getMessage(), true);
        }
    }

    private void sample(long time) {
        double position = (phase++ % 40) / 40.0;
        double value = "Triangle".equals(pattern)
            ? 1 - Math.abs(2 * position - 1)
            : (Math.sin(position * Math.PI * 2) + 1) / 2;
        times.add(time);
        wave.add(amplitude * value);
        reference.add(amplitude * .5);
        if (times.size() > 120) { times.remove(0); wave.remove(0); reference.remove(0); }
    }

    private void publishEntities() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("unit", "%"); metadata.put("stateClass", "measurement"); metadata.put("accuracyDecimals", 2);
        Double value = showChart && !wave.isEmpty() ? wave.get(wave.size() - 1) : null;
        host.publishSensor("wave", "Simulated wave", metadata, value);
        host.publishTextSensor("status", "Demo status", showChart ? "Chart running" : "Chart hidden");
        host.publishBinarySensor("chart_active", "Demo chart active", "", showChart);
        host.publishSwitch("chart", "Demo chart", showChart);
        host.publishSelect("pattern", "Demo pattern", new String[] {"Sine", "Triangle"}, pattern);
        // The same entities appear as local readings, even without Home Assistant.
        Map<String, Object> countMetadata = new LinkedHashMap<>();
        countMetadata.put("accuracyDecimals", 0);
        host.publishSensor("samples", "Samples in history", countMetadata, (double) times.size());
        host.publishTextSensor("summary", "Sample details",
            "Pattern: " + pattern + "\nHistory: up to 120 samples\nInterval: 2 seconds");
    }

    private void publishChart() {
        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("title", "Simulated activity");
        chart.put("unit", "%");
        chart.put("compact", compactChart);
        chart.put("type", chartType);
        chart.put("timestamps", new ArrayList<>(times));
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("name", "Wave"); first.put("color", seriesColor); first.put("values", new ArrayList<>(wave));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("name", "Reference"); second.put("values", new ArrayList<>(reference));
        chart.put("series", Arrays.asList(first, second));
        host.publishSeries("demo", chart);
    }

    @Override
    public synchronized void execute(String command, Map<String, Object> arguments) {
        if ("show".equals(command)) show();
        else if ("showOverlayBar".equals(command)) overlays.showBar();
        else if ("hideOverlayBar".equals(command)) overlays.hideBar();
        else if ("showFullOverlay".equals(command)) overlays.showFull();
        else if ("showGlow".equals(command)) overlays.showGlow();
        else if ("hideGlow".equals(command)) overlays.hideGlow();
        else if ("toggleEntity".equals(command)) homeAssistant.toggle();
        else if ("hide".equals(command)) {
            visible = false;
            host.hideWindow();
        } else throw new IllegalArgumentException("Unknown command: " + command);
    }

    @Override
    public synchronized void onEvent(String event, Map<String, Object> payload) {
        homeAssistant.onEvent(event, payload);
        keys.onEvent(event, payload);
        voice.onEvent(event, payload);
        voiceSkin.onEvent(event, payload);
        intercom.onEvent(event, payload);
        setup.onEvent(event, payload);
        if ("shizuku.state".equals(event)) {
            shizuku.stateChanged();
        } else if ("window.action".equals(event)) {
            greetings++;
            show();
        } else if ("window.closed".equals(event)) visible = false;
        else if ("switch.chart".equals(event)) {
            Object on = payload.get("on");
            if (!(on instanceof Boolean)) throw new IllegalArgumentException("Chart switch requires a boolean");
            Map<String, Object> next = new LinkedHashMap<>(savedSettings);
            next.put("showChart", on);
            configure(next);
            host.saveSettings(next);
        } else if ("select.pattern".equals(event)) {
            Object option = payload.get("option");
            if (!"Sine".equals(option) && !"Triangle".equals(option)) throw new IllegalArgumentException("Unknown pattern");
            Map<String, Object> next = new LinkedHashMap<>(savedSettings);
            next.put("pattern", option);
            configure(next);
            host.saveSettings(next);
        }
    }

    private void show() {
        visible = true;
        host.showWindow("Hello World", message + (greetings == 0 ? "" : "\nGreetings: " + greetings), "Say hello");
    }

    @Override
    public synchronized void stop() {
        // KS revokes the host and removes its windows, charts and entities before stop.
        shizuku.stop();
        homeAssistant.stop();
        statusTile.stop();
        keys.stop();
        voice.stop();
        voiceSkin.stop();
        intercom.stop();
        overlays.stop();
        setup.stop();
        if (sampler != null) { sampler.shutdownNow(); sampler = null; }
        times.clear(); wave.clear(); reference.clear(); phase = 0;
        visible = false;
        showChart = false;
        screensaverOptions = null;
        host = null;
    }
}
