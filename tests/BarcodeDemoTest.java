// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.*;
import me.jxl.kiosk.plugins.PluginHost;

public final class BarcodeDemoTest {
    static final class Host implements PluginHost {
        final Map<String, String> values = new HashMap<>();
        final List<Map<String, Object>> haCalls = new ArrayList<>();
        Map<String, Object> options;
        PluginHost.CommandCallback scan;
        int scans;
        public void scanBarcode(Map<String, Object> options, PluginHost.CommandCallback callback) { this.options = options; scan = callback; scans++; }
        public void publishTextSensor(String key, String name, String value) { values.put(key, value); }
        public void haCall(Map<String, Object> message, PluginHost.CommandCallback callback) { haCalls.add(message); }
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) {}
    }

    public static void main(String[] args) {
        Host host = new Host();
        BarcodeDemo demo = new BarcodeDemo();
        demo.start(host);
        assert host.values.containsKey("last_code") && host.values.get("last_code") == null;
        demo.scan();
        assert !host.options.containsKey("formats");
        assert Integer.valueOf(30).equals(host.options.get("timeoutSeconds"));
        // A second press while the scanner is open leaves it open.
        demo.scan();
        assert host.scans == 1;

        Map<String, Object> read = new HashMap<>();
        read.put("value", "4006381333931");
        read.put("format", "ean_13");
        host.scan.onResult(true, read, null);
        assert "4006381333931".equals(host.values.get("last_code"));
        assert "ean_13".equals(host.values.get("last_code_format"));
        Map<String, Object> event = host.haCalls.get(0);
        assert "fire_event".equals(event.get("type")) && BarcodeDemo.EVENT.equals(event.get("event_type"));
        assert "4006381333931".equals(((Map<?, ?>) event.get("event_data")).get("value"));

        // A scan that ends without a code keeps the last one.
        demo.scan();
        assert host.scans == 2;
        host.scan.onResult(false, Collections.singletonMap("code", "timeout"), "timeout");
        assert "4006381333931".equals(host.values.get("last_code"));
        assert host.haCalls.size() == 1;
        demo.scan();
        assert host.scans == 3;
        demo.stop();
        System.out.println("Barcode demo checks passed.");
    }
}
