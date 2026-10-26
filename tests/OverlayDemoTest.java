// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.*;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/** Checks what the demo asks KS for. Drawing the views needs a device. */
public final class OverlayDemoTest {
    static final class Host implements PluginHost {
        final Map<String, OverlaySpec> shown = new LinkedHashMap<>();
        final List<String> calls = new ArrayList<>();
        int[] insets;
        boolean older;
        String status;
        public void showOverlay(String key, OverlaySpec spec, OverlayFactory factory) {
            if (older) throw new UnsupportedOperationException("Native overlays are unavailable");
            assert factory != null;
            shown.put(key, spec); calls.add("show " + key);
        }
        public void hideOverlay(String key) {
            if (older) throw new UnsupportedOperationException("Native overlays are unavailable");
            shown.remove(key); calls.add("hide " + key);
        }
        public void setContentInsets(int left, int top, int right, int bottom) {
            if (older) throw new UnsupportedOperationException("Content insets are unavailable");
            insets = new int[] {left, top, right, bottom};
        }
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) {}
        public void status(String message, boolean error) { status = message; assert error; }
    }

    public static void main(String[] args) {
        Host host = new Host();
        OverlayDemo demo = new OverlayDemo(new ActionDemo());

        // Nothing shows before start or until an action asks for it.
        demo.showBar();
        assert host.calls.isEmpty();
        demo.start(host);
        assert host.calls.isEmpty();

        demo.showBar();
        OverlaySpec bar = host.shown.get(OverlayDemo.BAR);
        assert OverlaySpec.TOP.equals(bar.anchor()) && bar.inset() == 16;
        assert bar.width() == OverlaySpec.WRAP && bar.height() == OverlaySpec.WRAP;
        assert !bar.closeOnBack() && !bar.onTop() && bar.touchable();
        assert OverlaySpec.SLIDE.equals(bar.enter()) && OverlaySpec.SLIDE.equals(bar.exit()) && bar.transitionDuration() == 250;

        // The menu opens under the bar, moved toward its More button, and closes on a tap outside.
        demo.showMenu(60, -500);
        OverlaySpec menu = host.shown.get(OverlayDemo.MENU);
        assert OverlaySpec.TOP.equals(menu.anchor()) && menu.insetTop() == OverlayDemo.BAR_INSET + 60 + 8;
        assert menu.insetLeft() == 0 && menu.insetRight() == 0 && menu.insetBottom() == 0 && menu.inset() == 84;
        assert menu.offsetX() == -500 && menu.offsetY() == 0 && menu.closeOnOutsideTouch() && !menu.passOutsideTouch();
        assert OverlaySpec.SCALE.equals(menu.enter()) && OverlaySpec.FADE.equals(menu.exit()) && menu.closeOnBack();
        demo.hideMenu();
        assert !host.shown.containsKey(OverlayDemo.MENU);

        demo.showFull();
        OverlaySpec full = host.shown.get(OverlayDemo.FULL);
        assert full.width() == OverlaySpec.FILL && full.height() == OverlaySpec.FILL && full.closeOnBack();

        demo.showGlow();
        OverlaySpec glow = host.shown.get(OverlayDemo.GLOW);
        assert glow.width() == OverlaySpec.FILL && glow.onTop() && !glow.touchable() && glow.closeOnBack();
        assert OverlaySpec.FADE.equals(glow.enter()) && glow.transitionDuration() == 600;
        demo.hideGlow();
        assert !host.shown.containsKey(OverlayDemo.GLOW);

        demo.hideBar();
        // Hiding the bar gives its space back to the dashboard.
        assert java.util.Arrays.equals(host.insets, new int[] {0, 0, 0, 0});
        demo.reserve(84);
        assert host.insets[1] == 84;
        assert !host.shown.containsKey(OverlayDemo.BAR) && host.shown.containsKey(OverlayDemo.FULL);

        demo.stop();
        demo.showFull();
        // Hiding the bar takes its menu along.
        assert host.calls.size() == 8 && host.calls.get(6).equals("hide menu");

        Host older = new Host();
        older.older = true;
        OverlayDemo fallback = new OverlayDemo(new ActionDemo());
        fallback.start(older);
        fallback.showBar();
        assert older.status.contains("newer Kiosk Satellite");
        fallback.hideBar();
        System.out.println("Overlay demo tests passed.");
    }
}
