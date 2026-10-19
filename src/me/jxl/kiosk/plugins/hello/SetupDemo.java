// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * Settings buttons and plugin choices against a pretend device service. Test checks the address, Pair asks
 * the person to trust a fingerprint partway through, and Refresh on the Device setting looks for devices.
 * Picking a device fills in Device name. Callbacks must return promptly, so the work runs on its own
 * thread and reports back through setupResult.
 */
final class SetupDemo {
    /** What the pretend service finds: the ID the setting stores and the label the picker shows. */
    static final String[][] DEVICES = {
        {"lamp-kitchen", "Kitchen lamp"}, {"lamp-hall", "Hallway lamp"}, {"strip-desk", "Desk light strip"},
    };

    /** Runs a step later. Tests run the steps themselves. */
    interface Scheduler {
        void later(long delayMs, Runnable step);
        void stop();
    }

    private final Scheduler scheduler;
    private PluginHost host;
    private String address = "";
    private String device = "";
    private String deviceName = "";
    // Runs the host canceled, replaced or timed out. Their remaining steps do nothing.
    private final Set<String> ended = new HashSet<>();
    // The pairing run waiting for the person to answer.
    private String waiting;

    SetupDemo() { this(threadScheduler()); }

    SetupDemo(Scheduler scheduler) { this.scheduler = scheduler; }

    synchronized void start(PluginHost host) { this.host = host; }

    synchronized void configure(Map<String, Object> settings) {
        address = text(settings.get("setupAddress"));
        deviceName = text(settings.get("setupDeviceName"));
        String next = text(settings.get("setupDevice"));
        boolean picked = !next.equals(device);
        device = next;
        if (picked) fillName();
    }

    /** Settings events arrive without a subscription, and only for this plugin's own buttons and choices. */
    synchronized void onEvent(String event, Map<String, Object> payload) {
        Object run = payload.get("run");
        if (!(run instanceof String)) return;
        String id = (String) run;
        if ("settings.button".equals(event) && "setupTest".equals(payload.get("id"))) test(id);
        else if ("settings.button".equals(event) && "setupPair".equals(payload.get("id"))) pair(id);
        else if ("settings.choices".equals(event) && "setupDevice".equals(payload.get("key"))) scan(id);
        else if ("settings.cancel".equals(event)) {
            ended.add(id);
            if (id.equals(waiting)) waiting = null;
        } else if ("settings.confirm".equals(event) && id.equals(waiting)) {
            waiting = null;
            if (Boolean.TRUE.equals(payload.get("accepted"))) {
                report(id, "running", "Pairing");
                scheduler.later(1000, () -> finish(id, "success", "Paired with " + address + "."));
            } else {
                finish(id, "error", "Not paired. The device was not trusted.");
            }
        }
    }

    private void test(String run) {
        report(run, "running", "Connecting to " + address);
        // Any address with "fail" in it fails, to show an error.
        scheduler.later(1500, () -> {
            if (address.contains("fail")) finish(run, "error", "Could not reach " + address + ".");
            else finish(run, "success", "Reached " + address + " in 42 ms.");
        });
    }

    private void pair(String run) {
        report(run, "running", "Waiting for the device");
        scheduler.later(1000, () -> {
            synchronized (this) {
                if (ended.contains(run) || host == null) return;
                // The question goes to the person who pressed Pair. Its answer arrives as settings.confirm.
                if (host.confirmSetup(run, "Trust this device?",
                        "Fingerprint SHA-256 4F:9A:21:C3:7E:05:B8:D2\nCheck that it matches the label on the device.", "Trust")) {
                    waiting = run;
                }
            }
        });
    }

    private void scan(String run) {
        report(run, "running", "Looking for devices");
        for (int found = 1; found <= DEVICES.length; found++) {
            int count = found;
            scheduler.later(700L * found, () -> {
                synchronized (this) {
                    // Reporting first tells the demo whether the run is still wanted.
                    report(run, "running", "Found " + count + " of " + DEVICES.length);
                    if (ended.contains(run) || host == null) return;
                    publishChoices(count);
                    if (count == DEVICES.length) finish(run, "success", "Found " + count + " devices.");
                }
            });
        }
    }

    private void publishChoices(int count) {
        String[] ids = new String[count];
        String[] labels = new String[count];
        for (int i = 0; i < count; i++) { ids[i] = DEVICES[i][0]; labels[i] = DEVICES[i][1]; }
        host.setSettingChoices("setupDevice", ids, labels);
    }

    /** A picked device names itself. Every other saved setting stays as it is. */
    private void fillName() {
        String label = "";
        for (String[] known : DEVICES) if (known[0].equals(device)) label = known[1];
        if (label.isEmpty() || label.equals(deviceName)) return;
        try {
            host.updateSettings(Collections.<String, Object>singletonMap("setupDeviceName", label));
            deviceName = label;
        } catch (UnsupportedOperationException | LinkageError olderApp) {
            // Older Kiosk Satellite versions show Device as a text field and have no updateSettings.
        }
    }

    private synchronized void report(String run, String state, String message) {
        if (ended.contains(run) || host == null) return;
        // False means the run is over. Stop its work.
        if (!host.setupResult(run, state, message)) ended.add(run);
    }

    private synchronized void finish(String run, String state, String message) {
        report(run, state, message);
        ended.add(run);
    }

    synchronized void stop() {
        scheduler.stop();
        ended.clear();
        waiting = null;
        host = null;
    }

    private static String text(Object value) { return value instanceof String ? (String) value : ""; }

    private static Scheduler threadScheduler() {
        return new Scheduler() {
            private ScheduledExecutorService executor;
            @Override public synchronized void later(long delayMs, Runnable step) {
                if (executor == null) executor = Executors.newSingleThreadScheduledExecutor(task -> {
                    Thread thread = new Thread(task, "hello-world-setup");
                    thread.setDaemon(true);
                    return thread;
                });
                executor.schedule(step, delayMs, TimeUnit.MILLISECONDS);
            }
            @Override public synchronized void stop() {
                if (executor != null) executor.shutdownNow();
                executor = null;
            }
        };
    }
}
