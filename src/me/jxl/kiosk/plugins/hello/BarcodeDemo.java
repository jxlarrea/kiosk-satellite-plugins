// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.LinkedHashMap;
import java.util.Map;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * The KS barcode scanner: the Scan a code action opens it, the code it
 * reads lands in the Last code readings and Home Assistant hears it as a
 * hello_world_barcode event. Expose the action as an ESPHome button to
 * start a scan from an automation.
 */
final class BarcodeDemo {
    static final String EVENT = "hello_world_barcode";
    private PluginHost host;
    private boolean scanning;

    synchronized void start(PluginHost host) {
        this.host = host;
        host.publishTextSensor("last_code", "Last code", null);
        host.publishTextSensor("last_code_format", "Last code format", null);
    }

    /** The Scan a code action. A scan already open stays open. */
    synchronized void scan() {
        if (host == null || scanning) return;
        // No formats, so it reads every kind. A plugin that expects one kind lists it and reads faster.
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("timeoutSeconds", 30);
        options.put("title", "Scan a code");
        options.put("message", "Hold a barcode or a QR code inside the frame.");
        scanning = true;
        try {
            host.scanBarcode(options, this::finished);
        } catch (UnsupportedOperationException | LinkageError olderHost) {
            scanning = false;
            host.status("Barcode scanning needs a newer Kiosk Satellite.", true);
        }
    }

    private synchronized void finished(boolean ok, Object data, String error) {
        scanning = false;
        if (host == null) return;
        if (!ok) {
            // canceled, timeout, busy, disabled, permission, no_camera, camera_failed or unavailable.
            host.log("Barcode scan ended: " + error);
            return;
        }
        Map<?, ?> result = (Map<?, ?>) data;
        String value = (String) result.get("value");
        String format = (String) result.get("format");
        host.publishTextSensor("last_code", "Last code", value.length() > 512 ? value.substring(0, 512) : value);
        host.publishTextSensor("last_code_format", "Last code format", format);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("value", value);
        event.put("format", format);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "fire_event");
        message.put("event_type", EVENT);
        message.put("event_data", event);
        PluginHost current = host;
        current.haCall(message, (sent, reply, failure) -> {
            if (!sent) current.log("Could not send the code to Home Assistant: " + failure);
        });
    }

    synchronized void stop() {
        host = null;
        scanning = false;
    }
}
