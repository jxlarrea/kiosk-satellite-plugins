// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import java.util.LinkedHashMap;
import java.util.Map;
import me.jxl.kiosk.plugins.CameraFrame;
import me.jxl.kiosk.plugins.KsTheme;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * Frames from the device camera: Show camera preview opens the camera for
 * 30 seconds and draws its frames in a small overlay, and the picture's
 * average brightness is a reading. Frames arrive on a camera thread of the
 * plugin's own, so the preview is posted to the main thread.
 */
final class CameraDemo {
    static final String OVERLAY = "camera";
    private PluginHost host;
    private boolean open;
    private long lastReadingNs;
    private Handler main;
    /** The preview while its overlay shows. Touched on the main thread only. */
    private ImageView view;

    synchronized void start(PluginHost host) { this.host = host; }

    /** The Show camera preview action. Showing it again restarts the 30 seconds. */
    synchronized void show() {
        if (host == null) return;
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("size", "320x240");
        options.put("fps", 10);
        // Color. Leave the format out for brightness alone, which costs less.
        options.put("format", "nv21");
        options.put("timeoutSeconds", 30);
        try {
            host.openCamera(options, new PluginHost.CameraListener() {
                @Override public void onFrame(CameraFrame frame) { frame(frame); }
                @Override public void onClosed(String reason) { closed(reason); }
            });
            open = true;
            host.showOverlay(OVERLAY, OverlayDemo.animated(OverlaySpec.at(OverlaySpec.BOTTOM_RIGHT).inset(16).size(240, 240),
                OverlaySpec.FADE, OverlaySpec.FADE, 200), new Preview());
        } catch (UnsupportedOperationException | LinkageError olderHost) {
            host.status("The camera demo needs a newer Kiosk Satellite.", true);
        }
    }

    /** The Hide camera preview action. */
    synchronized void hide() {
        if (host == null || !open) return;
        host.closeCamera();
    }

    /** Back closed the preview, so the camera goes too. */
    synchronized void onEvent(String event, Map<String, Object> payload) {
        if ("overlay.closed".equals(event) && OVERLAY.equals(payload.get("key"))) hide();
    }

    private synchronized void closed(String reason) {
        open = false;
        if (host == null) return;
        host.hideOverlay(OVERLAY);
        host.publishSensor("camera_brightness", "Camera brightness", metadata(), null);
        // closed, timeout, busy, disabled, permission, no_camera or camera_failed.
        host.log("Camera closed: " + reason);
    }

    private void frame(CameraFrame frame) {
        PluginHost current;
        synchronized (this) {
            current = host;
            if (current == null) return;
            // One reading a second is plenty for Home Assistant.
            long now = System.nanoTime();
            if (now - lastReadingNs >= 1_000_000_000L) {
                lastReadingNs = now;
                current.publishSensor("camera_brightness", "Camera brightness", metadata(), brightness(frame.data(), frame.width() * frame.height()));
            }
        }
        if (main == null) main = new Handler(Looper.getMainLooper());
        Bitmap picture = picture(frame);
        main.post(() -> { if (view != null) view.setImageBitmap(picture); });
    }

    /** The average of the first size luminance bytes, as a percentage. Samples every 16th pixel. */
    static double brightness(byte[] data, int size) {
        long sum = 0;
        int count = 0;
        for (int i = 0; i < size; i += 16) { sum += data[i] & 0xFF; count++; }
        return count == 0 ? 0 : Math.round(sum * 100.0 / (count * 255.0));
    }

    /** The frame in color, turned upright and mirrored for a front camera. */
    private static Bitmap picture(CameraFrame frame) {
        int width = frame.width();
        int height = frame.height();
        Bitmap raw = Bitmap.createBitmap(rgb(frame.data(), width, height), width, height, Bitmap.Config.ARGB_8888);
        Matrix matrix = new Matrix();
        matrix.postRotate(frame.rotationDegrees());
        if (frame.frontFacing()) matrix.postScale(-1, 1);
        return Bitmap.createBitmap(raw, 0, 0, width, height, matrix, false);
    }

    /** NV21 to ARGB pixels: the brightness plane, then V and U for each 2 by 2 block. */
    static int[] rgb(byte[] nv21, int width, int height) {
        int[] pixels = new int[width * height];
        int chroma = width * height;
        int chromaWidth = (width + 1) / 2;
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                int y = nv21[row * width + column] & 0xFF;
                int at = chroma + 2 * ((row / 2) * chromaWidth + column / 2);
                int v = (nv21[at] & 0xFF) - 128;
                int u = (nv21[at + 1] & 0xFF) - 128;
                int r = clamp(y + (int) (1.402f * v));
                int g = clamp(y - (int) (0.344f * u + 0.714f * v));
                int b = clamp(y + (int) (1.772f * u));
                pixels[row * width + column] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return pixels;
    }

    private static int clamp(int value) { return value < 0 ? 0 : Math.min(value, 255); }

    private static Map<String, Object> metadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("unit", "%");
        metadata.put("stateClass", "measurement");
        metadata.put("accuracyDecimals", 0);
        return metadata;
    }

    synchronized void stop() {
        host = null;
        open = false;
    }

    private final class Preview implements OverlayFactory {
        @Override public View create(Context context, KsTheme theme) {
            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            // Black around a picture that does not fill the card.
            android.graphics.drawable.GradientDrawable card = theme.card();
            card.setColor(Color.BLACK);
            image.setBackground(card);
            image.setClipToOutline(true);
            view = image;
            return image;
        }

        @Override public void onDestroy(View destroyed) {
            if (view == destroyed) view = null;
        }
    }
}
