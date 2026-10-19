// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.*;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

public final class CameraDemoTest {
    static final class Host implements PluginHost {
        final Map<String, Double> sensors = new HashMap<>();
        final Set<String> overlays = new HashSet<>();
        Map<String, Object> options;
        PluginHost.CameraListener listener;
        int closes;
        public void openCamera(Map<String, Object> options, PluginHost.CameraListener listener) { this.options = options; this.listener = listener; }
        public void closeCamera() { closes++; listener.onClosed("closed"); }
        public void showOverlay(String key, OverlaySpec spec, OverlayFactory factory) { overlays.add(key); }
        public void hideOverlay(String key) { overlays.remove(key); }
        public void publishSensor(String key, String name, Map<String, Object> metadata, Double state) { sensors.put(key, state); }
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) {}
    }

    public static void main(String[] args) {
        assert CameraDemo.brightness(new byte[] {0, 0, 0, 0}, 4) == 0;
        byte[] white = new byte[64];
        Arrays.fill(white, (byte) 255);
        assert CameraDemo.brightness(white, 64) == 100;
        byte[] half = new byte[64];
        Arrays.fill(half, (byte) 128);
        assert CameraDemo.brightness(half, 64) == 50;

        // Neutral chroma is gray. Strong V is red.
        byte[] gray = {(byte) 100, (byte) 100, (byte) 100, (byte) 100, (byte) 128, (byte) 128};
        assert CameraDemo.rgb(gray, 2, 2)[3] == 0xFF646464;
        byte[] red = {(byte) 100, (byte) 100, (byte) 100, (byte) 100, (byte) 228, (byte) 128};
        int pixel = CameraDemo.rgb(red, 2, 2)[0];
        assert ((pixel >> 16) & 0xFF) == 240 && (pixel & 0xFF) == 100;

        Host host = new Host();
        CameraDemo demo = new CameraDemo();
        demo.start(host);
        demo.show();
        assert "320x240".equals(host.options.get("size"));
        assert "nv21".equals(host.options.get("format"));
        assert Integer.valueOf(30).equals(host.options.get("timeoutSeconds"));
        assert host.overlays.contains(CameraDemo.OVERLAY);

        // Back closing the preview closes the camera, which takes the overlay and reading down.
        demo.onEvent("overlay.closed", Collections.singletonMap("key", CameraDemo.OVERLAY));
        assert host.closes == 1;
        assert host.overlays.isEmpty();
        assert host.sensors.containsKey("camera_brightness") && host.sensors.get("camera_brightness") == null;
        // Nothing left to close.
        demo.hide();
        assert host.closes == 1;

        // The camera ending on its own does the same.
        demo.show();
        host.listener.onClosed("timeout");
        assert host.overlays.isEmpty();
        demo.stop();
        System.out.println("Camera demo checks passed.");
    }
}
