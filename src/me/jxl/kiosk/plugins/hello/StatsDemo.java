// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import me.jxl.kiosk.plugins.KsTheme;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * The kiosk's own readings from getStats, shown three ways: a native screensaver drawn with the KS theme, a chart
 * per tile and a grouped choice per reading with its current value. Each tile is a list row whose Reading field
 * takes plugin choices. Kiosk Satellite says when the settings page opens, so the demo reads the stats only while
 * someone looks at the page or the screensaver shows. The Use device stats screensaver action switches to it with
 * screensaver.mode, and Restore screensaver mode gives the choice back.
 */
final class StatsDemo {
    static final String KEY = "stats";
    static final String MODE = "plugin:hello-world:" + KEY;
    /** The Reading field of a Tiles row, by its dotted path. */
    static final String CHOICES = "statsTiles.reading";
    /** Each reading: the getStats field the choice stores, its label, group, category, icon and unit. */
    static final String[][] READINGS = {
        {"cpu", "CPU usage", "CPU", "Usage", "mdi:chip", "%"},
        {"cpuClock", "CPU clock", "CPU", "Clock", "mdi:speedometer", "MHz"},
        {"temp", "CPU temperature", "CPU", "Temperature", "mdi:thermometer", "°C"},
        {"battery", "Battery level", "Battery", "Charge", "mdi:battery", "%"},
        {"batteryTemp", "Battery temperature", "Battery", "Temperature", "mdi:thermometer", "°C"},
    };
    static final int HISTORY = 120;
    /** Reads closer together than this update the values but add no chart sample. */
    static final long SAMPLE_MS = 2000;

    private PluginHost host;
    private ScheduledExecutorService poller;
    private boolean pageOpen;
    private final List<Long> times = new ArrayList<>();
    private final Map<String, List<Double>> history = new LinkedHashMap<>();
    /** The chart keys this session published, so a tile taken away removes its own chart only. */
    private final Set<String> charts = new HashSet<>();
    /** Read by the views on the main thread. */
    private volatile List<String> tiles = Collections.emptyList();
    private volatile Map<?, ?> latest = Collections.emptyMap();
    private volatile double warnTemp = 70;
    private final List<Board> boards = new CopyOnWriteArrayList<>();

    synchronized void start(PluginHost host) {
        this.host = host;
        // Labels before the first read, so a saved tile shows its name.
        publishChoices(false);
        try {
            host.publishScreensaverView(KEY, "Device stats", new Screen());
        } catch (UnsupportedOperationException | LinkageError olderHost) {
            host.log("Native screensavers need a newer Kiosk Satellite.");
        }
        readMode();
        poller = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "hello-world-stats");
            thread.setDaemon(true);
            return thread;
        });
        poller.scheduleWithFixedDelay(this::poll, 3, 3, TimeUnit.SECONDS);
    }

    synchronized void configure(Map<String, Object> settings) {
        tiles = tiles(settings.get("statsTiles"));
        Object warn = settings.get("statsWarnTemp");
        warnTemp = warn instanceof Number ? ((Number) warn).doubleValue() : 70;
        // A tile taken away takes its chart with it.
        for (String key : new ArrayList<>(charts)) {
            if (tiles.contains(reading(key))) continue;
            host.removeSeries(key);
            charts.remove(key);
        }
        if (!times.isEmpty()) publishCharts();
        redraw();
    }

    /** Settings events arrive without a subscription, and only for this plugin. */
    synchronized void onEvent(String event, Map<String, Object> payload) {
        if (host == null) return;
        if ("settings.page".equals(event)) {
            pageOpen = Boolean.TRUE.equals(payload.get("open"));
            if (pageOpen) { read(null); readMode(); }
        } else if ("settings.choices".equals(event) && CHOICES.equals(payload.get("key")) && payload.get("run") instanceof String) {
            String run = (String) payload.get("run");
            host.setupResult(run, "running", "Reading this kiosk's stats");
            read(run);
        }
    }

    /** The Use device stats screensaver action. It shows the next time the screensaver starts. */
    synchronized void useScreensaver() {
        if (host == null) return;
        PluginHost current = host;
        host.executeCommand("setScreensaverMode", Collections.singletonMap("mode", MODE), (ok, data, error) -> {
            if (!ok) current.status("Could not switch the screensaver: " + error, true);
            readMode();
        });
    }

    /** The Restore screensaver mode action. The schedule or the saved Screensaver mode applies again. */
    synchronized void restoreScreensaver() {
        if (host == null) return;
        host.executeCommand("clearScreensaverMode", Collections.emptyMap(), (ok, data, error) -> readMode());
    }

    /** Reads only while something shows the values. */
    synchronized void poll() {
        if (host == null || !pageOpen && !shown()) return;
        read(null);
    }

    private boolean shown() {
        for (Board board : boards) if (board.visible) return true;
        return false;
    }

    private void read(String run) {
        PluginHost current = host;
        host.executeCommand("getStats", Collections.emptyMap(), (ok, data, error) -> {
            synchronized (this) {
                if (host != current) return;
                if (ok) received((Map<?, ?>) data, System.currentTimeMillis());
                if (run == null) return;
                if (ok) {
                    publishChoices(true);
                    host.setupResult(run, "success", "Listed " + READINGS.length + " readings.");
                } else host.setupResult(run, "error", "Could not read the stats: " + error);
            }
        });
    }

    synchronized void received(Map<?, ?> stats, long now) {
        latest = stats;
        // Fresh values in the picker, while someone can see them.
        if (pageOpen) publishChoices(true);
        redraw();
        if (!times.isEmpty() && now - times.get(times.size() - 1) < SAMPLE_MS) return;
        times.add(now);
        for (String[] reading : READINGS) {
            List<Double> values = history.computeIfAbsent(reading[0], id -> new ArrayList<>());
            Object value = stats.get(reading[0]);
            values.add(value instanceof Number ? ((Number) value).doubleValue() : null);
            if (values.size() > HISTORY) values.remove(0);
        }
        if (times.size() > HISTORY) times.remove(0);
        publishCharts();
    }

    /** One chart per tile. The Device stats demo group claims them with "charts": ["*"], since the keys depend on the tiles. */
    private void publishCharts() {
        for (String id : tiles) {
            String[] reading = find(id);
            Map<String, Object> chart = new LinkedHashMap<>();
            chart.put("title", reading[1]);
            chart.put("unit", reading[5]);
            chart.put("compact", true);
            chart.put("timestamps", new ArrayList<>(times));
            Map<String, Object> series = new LinkedHashMap<>();
            series.put("name", reading[1]);
            series.put("values", new ArrayList<>(history.get(id)));
            chart.put("series", Collections.singletonList(series));
            try {
                host.publishSeries(chartKey(id), chart);
                charts.add(chartKey(id));
            } catch (RuntimeException rateLimited) {
                // The next read publishes it again.
            }
        }
    }

    /** Grouped choices: a heading per group, the current value on the right and a chip per category. */
    private void publishChoices(boolean withValues) {
        List<Map<String, Object>> choices = new ArrayList<>();
        for (String[] reading : READINGS) {
            Map<String, Object> choice = new LinkedHashMap<>();
            choice.put("id", reading[0]);
            choice.put("label", reading[1]);
            choice.put("group", reading[2]);
            choice.put("detail", reading[0] + " in getStats");
            if (withValues) choice.put("value", format(reading[0], latest.get(reading[0])));
            choice.put("category", reading[3]);
            choice.put("icon", reading[4]);
            choices.add(choice);
        }
        try {
            host.setSettingChoices(CHOICES, choices);
        } catch (UnsupportedOperationException | LinkageError olderHost) {
            // Kiosk Satellite versions without grouped choices refuse this manifest, so only tests get here.
        }
    }

    private void readMode() {
        PluginHost current = host;
        host.executeCommand("getScreensaverMode", Collections.emptyMap(), (ok, data, error) -> {
            synchronized (this) {
                if (host != current) return;
                host.publishTextSensor("screensaver_mode", "Screensaver mode", ok ? mode((Map<?, ?>) data) : "Not available: " + error);
            }
        });
    }

    /** The mode that shows, by its label, and what chose it. */
    static String mode(Map<?, ?> state) {
        Object mode = state.get("mode");
        String label = String.valueOf(mode);
        Object modes = state.get("modes");
        if (modes instanceof List) for (Object entry : (List<?>) modes) {
            if (entry instanceof Map && mode != null && mode.equals(((Map<?, ?>) entry).get("id"))) label = String.valueOf(((Map<?, ?>) entry).get("label"));
        }
        if (state.get("override") != null) return label + "\nSet by a plugin";
        if (state.get("schedule") != null) return label + "\nFrom the schedule";
        return label + "\nFrom Screensaver mode";
    }

    /** The readings the tiles picked, in order. A reading picked twice shows once, and an unknown one not at all. */
    static List<String> tiles(Object value) {
        List<String> picked = new ArrayList<>();
        if (!(value instanceof List)) return picked;
        for (Object row : (List<?>) value) {
            Object id = row instanceof Map ? ((Map<?, ?>) row).get("reading") : null;
            if (id instanceof String && find((String) id) != null && !picked.contains(id)) picked.add((String) id);
        }
        return Collections.unmodifiableList(picked);
    }

    static String format(String id, Object value) {
        if (!(value instanceof Number)) return "Not available";
        double number = ((Number) value).doubleValue();
        String unit = find(id)[5];
        if ("°C".equals(unit)) return String.format(java.util.Locale.US, "%.1f °C", number);
        if ("MHz".equals(unit)) return Math.round(number) + " MHz";
        return Math.round(number) + unit;
    }

    static String[] find(String id) {
        for (String[] reading : READINGS) if (reading[0].equals(id)) return reading;
        return null;
    }

    /** cpuClock becomes stats_cpu_clock. Chart keys are lowercase. */
    static String chartKey(String id) {
        return "stats_" + id.replaceAll("([A-Z])", "_$1").toLowerCase(java.util.Locale.US);
    }

    private static String reading(String chartKey) {
        for (String[] reading : READINGS) if (chartKey(reading[0]).equals(chartKey)) return reading[0];
        return null;
    }

    private void redraw() {
        for (Board board : boards) board.post(board::refresh);
    }

    synchronized void stop() {
        // KS revokes the host and removes the screensaver and charts before stop.
        if (poller != null) { poller.shutdownNow(); poller = null; }
        times.clear(); history.clear(); charts.clear();
        latest = Collections.emptyMap();
        pageOpen = false;
        host = null;
    }

    /** KS builds the board each time the screensaver shows and destroys it when it goes away. */
    private final class Screen implements OverlayFactory {
        @Override public View create(Context context, KsTheme theme) {
            Board board = new Board(context, theme);
            boards.add(board);
            return board;
        }

        @Override public void onThemeChanged(View view, KsTheme theme) {
            ((Board) view).theme = theme;
            ((Board) view).rebuild();
        }

        @Override public void onDestroy(View view) {
            boards.remove(view);
        }
    }

    /** A card per tile, side by side on a wide screen and stacked on a tall one. */
    private final class Board extends LinearLayout {
        KsTheme theme;
        /** False while the screen is off or the screensaver is covered, so polling stops. */
        volatile boolean visible;
        private List<String> built = Collections.emptyList();

        Board(Context context, KsTheme theme) {
            super(context);
            this.theme = theme;
            setGravity(Gravity.CENTER);
            setBackgroundColor(Color.BLACK);
            rebuild();
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            setOrientation(MeasureSpec.getSize(widthSpec) >= MeasureSpec.getSize(heightSpec) ? HORIZONTAL : VERTICAL);
            super.onMeasure(widthSpec, heightSpec);
        }

        @Override protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            visible = visibility == VISIBLE;
            if (visible) tap(StatsDemo.this::poll);
        }

        void rebuild() {
            removeAllViews();
            built = tiles;
            for (String id : built) {
                String[] reading = find(id);
                LinearLayout card = new LinearLayout(getContext());
                card.setOrientation(VERTICAL);
                card.setGravity(Gravity.CENTER);
                card.setBackground(theme.card());
                card.setPadding(theme.px(28), theme.px(24), theme.px(28), theme.px(24));
                card.setMinimumWidth(theme.px(180));
                ImageView icon = new ImageView(getContext());
                icon.setImageDrawable(OverlayDemo.icon(theme, reading[4], "primary"));
                TextView value = new TextView(getContext());
                theme.styleText(value, 34, 500, "onSurface");
                // One line, so a wider value widens its card instead of wrapping.
                value.setSingleLine(true);
                TextView label = new TextView(getContext());
                theme.styleText(label, 15, 400, "onSurfaceVariant");
                label.setText(reading[1]);
                card.addView(icon, new LayoutParams(theme.px(32), theme.px(32)));
                card.addView(value, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
                card.addView(label, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
                LayoutParams gap = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                gap.setMargins(theme.px(8), theme.px(8), theme.px(8), theme.px(8));
                addView(card, gap);
            }
            refresh();
        }

        /** New values in place. A changed set of tiles builds the cards again. */
        void refresh() {
            if (!built.equals(tiles)) { rebuild(); return; }
            for (int i = 0; i < built.size(); i++) {
                String id = built.get(i);
                Object value = latest.get(id);
                TextView text = (TextView) ((LinearLayout) getChildAt(i)).getChildAt(1);
                text.setText(latest.containsKey(id) ? format(id, value) : "...");
                boolean hot = "°C".equals(find(id)[5]) && value instanceof Number && ((Number) value).doubleValue() > warnTemp;
                text.setTextColor(theme.color(hot ? "error" : "onSurface"));
            }
        }
    }

    /** Runs plugin code from the main thread. The session can end first, and an exception here would end the app. */
    private static void tap(Runnable action) {
        try { action.run(); } catch (RuntimeException sessionEnded) {}
    }
}
