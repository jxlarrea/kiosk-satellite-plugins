// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import me.jxl.kiosk.plugins.KsTheme;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * Native overlays drawn with the KS theme: a small bar at the top of the
 * kiosk with a menu that opens under its More button and lists the quick
 * actions from the Action demo settings, a full screen card
 * and an animated edge glow that draws over everything like the voice
 * overlay while every touch passes through. Views are built on the main
 * thread, so their click listeners run there too.
 */
final class OverlayDemo {
    static final String BAR = "bar";
    static final String MENU = "menu";
    static final String FULL = "full";
    static final String GLOW = "glow";
    /** Where the bar sits below the top edge, in dp. */
    static final int BAR_INSET = 16;
    private final ActionDemo actions;
    private PluginHost host;
    private volatile int greetings;
    private volatile int level = 50;
    /** The bar's greeting, so the menu's Say hello can update it. Touched on the main thread only. */
    private TextView barLabel;

    OverlayDemo(ActionDemo actions) { this.actions = actions; }

    synchronized void start(PluginHost host) { this.host = host; }

    /** The Show top bar action. Back leaves the bar alone, the way it would a status bar. */
    synchronized void showBar() {
        if (host == null) return;
        try {
            host.showOverlay(BAR, animated(OverlaySpec.at(OverlaySpec.TOP).inset(BAR_INSET).closeOnBack(false), OverlaySpec.SLIDE, OverlaySpec.SLIDE, 250), new Bar());
        } catch (UnsupportedOperationException olderHost) {
            host.status("Native overlays need a newer Kiosk Satellite.", true);
        }
    }

    /** The Hide top bar action. */
    synchronized void hideBar() {
        if (host == null) return;
        try { host.hideOverlay(MENU); host.hideOverlay(BAR); } catch (UnsupportedOperationException olderHost) {}
        reserve(0);
    }

    /**
     * Keeps the dashboard clear of the bar: Home Assistant pads its views
     * by this much from the top, so its first cards sit below the bar
     * instead of under it. Zero gives the space back.
     */
    synchronized void reserve(int topDp) {
        if (host == null) return;
        try {
            host.setContentInsets(0, topDp, 0, 0);
        } catch (UnsupportedOperationException | LinkageError olderHost) {
            // Older Kiosk Satellite versions leave the bar over the dashboard.
        }
    }

    /**
     * The bar's More button. The menu opens under the bar, centered on the
     * button: below the bar's depth, then moved sideways by how far the
     * button is from the middle of the screen. KS keeps it on screen, and a
     * tap anywhere else closes it.
     */
    synchronized void showMenu(int barHeightDp, int fromCenterDp) {
        if (host == null) return;
        OverlaySpec spec = OverlaySpec.at(OverlaySpec.TOP);
        try {
            spec.inset(0, BAR_INSET + barHeightDp + 8, 0, 0).offset(fromCenterDp, 0).closeOnOutsideTouch(true);
        } catch (LinkageError olderHost) {
            // Without per-edge insets the menu sits where the bar is, which still works.
            spec.inset(BAR_INSET);
        }
        host.showOverlay(MENU, animated(spec, OverlaySpec.SCALE, OverlaySpec.FADE, 160), new Menu());
    }

    synchronized void hideMenu() {
        if (host != null) host.hideOverlay(MENU);
    }

    /** Transitions where KS has them. Older versions show and remove overlays at once. */
    static OverlaySpec animated(OverlaySpec spec, String enter, String exit, int ms) {
        try {
            return spec.enter(enter).exit(exit).transitionDuration(ms);
        } catch (LinkageError olderHost) {
            return spec;
        }
    }

    /** A KS icon, or none on Kiosk Satellite versions without KsTheme.icon. */
    static Drawable icon(KsTheme theme, String name, String role) {
        try {
            return theme.icon(name, role);
        } catch (LinkageError olderHost) {
            return null;
        }
    }

    /** The Show full screen overlay action and the bar's Full screen button. Back closes it. */
    synchronized void showFull() {
        if (host == null) return;
        try {
            host.showOverlay(FULL, animated(OverlaySpec.fullScreen(), OverlaySpec.FADE, OverlaySpec.FADE, 200), new Full());
        } catch (UnsupportedOperationException olderHost) {
            host.status("Native overlays need a newer Kiosk Satellite.", true);
        }
    }

    /**
     * The Show edge glow action. Visual only: it covers the whole kiosk,
     * even the screensaver and camera views, and every touch goes through
     * to whatever is under it. Back still clears it.
     */
    synchronized void showGlow() {
        if (host == null) return;
        try {
            host.showOverlay(GLOW, animated(OverlaySpec.fullScreen().onTop(true).touchable(false), OverlaySpec.FADE, OverlaySpec.FADE, 600), new Glow());
        } catch (UnsupportedOperationException olderHost) {
            host.status("Native overlays need a newer Kiosk Satellite.", true);
        }
    }

    /** The Hide edge glow action. */
    synchronized void hideGlow() {
        if (host == null) return;
        try { host.hideOverlay(GLOW); } catch (UnsupportedOperationException olderHost) {}
    }

    private synchronized void hideFull() {
        if (host != null) host.hideOverlay(FULL);
    }

    /**
     * Click listeners run on the main thread, outside the callbacks KS
     * guards. The session can end between a tap and the host call, and an
     * exception thrown here would end the app, so it is dropped.
     */
    private static void tap(Runnable action) {
        try { action.run(); } catch (RuntimeException sessionEnded) {}
    }

    /** KS revokes the host and removes the overlays before stop, so nothing is hidden here. */
    synchronized void stop() { host = null; }

    private String greeting() {
        return greetings == 0 ? "Hello World" : "Hello World · " + greetings;
    }

    /** A pill with a label, a slider and two buttons. Its size follows its content. */
    private final class Bar implements OverlayFactory {
        @Override public View create(Context context, KsTheme theme) {
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = new TextView(context);
            SeekBar slider = new SeekBar(context);
            TextView value = new TextView(context);
            Button hello = new Button(context);
            Button full = new Button(context);
            ImageButton more = new ImageButton(context);
            label.setText(greeting());
            barLabel = label;
            slider.setMax(100);
            slider.setProgress(level);
            value.setText(level + "%");
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    level = progress;
                    value.setText(progress + "%");
                }
                @Override public void onStartTrackingTouch(SeekBar bar) {}
                @Override public void onStopTrackingTouch(SeekBar bar) {}
            });
            hello.setText("Say hello");
            full.setText("Full screen");
            hello.setOnClickListener(view -> {
                greetings++;
                label.setText(greeting());
            });
            full.setOnClickListener(view -> tap(OverlayDemo.this::showFull));
            more.setContentDescription("More");
            more.setOnClickListener(view -> tap(() -> {
                float density = view.getResources().getDisplayMetrics().density;
                int[] at = new int[2];
                view.getLocationOnScreen(at);
                int screen = view.getResources().getDisplayMetrics().widthPixels;
                int fromCenter = Math.round((at[0] + view.getWidth() / 2f - screen / 2f) / density);
                showMenu(Math.round(root.getHeight() / density), fromCenter);
            }));
            root.addView(label);
            LinearLayout.LayoutParams track = new LinearLayout.LayoutParams(theme.px(180), LinearLayout.LayoutParams.WRAP_CONTENT);
            track.setMarginStart(theme.px(12));
            root.addView(slider, track);
            // Wide enough for 100% so the bar does not resize while dragging.
            value.setMinWidth(theme.px(44));
            root.addView(value);
            LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            gap.setMarginStart(theme.px(8));
            root.addView(hello, gap);
            root.addView(full);
            root.addView(more, new LinearLayout.LayoutParams(theme.px(44), theme.px(44)));
            // The bar's depth, from the top edge to just below it, whenever its height changes.
            root.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (bottom - top == oldBottom - oldTop) return;
                int height = Math.round((bottom - top) / v.getResources().getDisplayMetrics().density);
                tap(() -> reserve(BAR_INSET + height + 8));
            });
            onThemeChanged(root, theme);
            return root;
        }

        @Override public void onThemeChanged(View view, KsTheme theme) {
            LinearLayout root = (LinearLayout) view;
            GradientDrawable pill = theme.card();
            pill.setCornerRadius(theme.px(100));
            root.setBackground(pill);
            root.setPadding(theme.px(20), theme.px(8), theme.px(8), theme.px(8));
            theme.styleText((TextView) root.getChildAt(0), 15, 500, "onSurface");
            theme.styleSlider((SeekBar) root.getChildAt(1));
            theme.styleText((TextView) root.getChildAt(2), 14, 500, "onSurfaceVariant");
            theme.stylePill((TextView) root.getChildAt(3), true);
            theme.stylePill((TextView) root.getChildAt(4), false);
            ImageButton more = (ImageButton) root.getChildAt(5);
            more.setImageDrawable(icon(theme, "mdi:dots-vertical", "onSurfaceVariant"));
            GradientDrawable disc = new GradientDrawable();
            disc.setShape(GradientDrawable.OVAL);
            disc.setColor(Color.TRANSPARENT);
            more.setBackground(disc);
            more.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        }
    }

    /**
     * A small card of actions, each with a KS icon, then the quick actions the user set up. Tapping one closes the
     * menu. A quick action runs whatever the user chose for it.
     */
    private final class Menu implements OverlayFactory {
        private final List<String[]> rows = new ArrayList<>();
        private final List<Runnable> taps = new ArrayList<>();

        Menu() {
            add("mdi:hand-wave", "Say hello", () -> {
                greetings++;
                if (barLabel != null) barLabel.setText(greeting());
            });
            add("mdi:fullscreen", "Full screen", OverlayDemo.this::showFull);
            add("mdi:close", "Hide top bar", OverlayDemo.this::hideBar);
            for (ActionDemo.QuickAction quick : actions.quickActions()) {
                add(quick.icon, quick.label, () -> actions.run(quick.label, quick.path));
            }
        }

        private void add(String icon, String label, Runnable tap) {
            rows.add(new String[] {icon, label});
            taps.add(tap);
        }

        @Override public View create(Context context, KsTheme theme) {
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < rows.size(); i++) {
                TextView row = new TextView(context);
                row.setText(rows.get(i)[1]);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinHeight(theme.px(48));
                final Runnable action = taps.get(i);
                row.setOnClickListener(view -> tap(() -> {
                    hideMenu();
                    action.run();
                }));
                card.addView(row, new LinearLayout.LayoutParams(theme.px(220), LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            onThemeChanged(card, theme);
            return card;
        }

        @Override public void onThemeChanged(View view, KsTheme theme) {
            LinearLayout card = (LinearLayout) view;
            card.setBackground(theme.card());
            card.setPadding(theme.px(8), theme.px(8), theme.px(8), theme.px(8));
            for (int i = 0; i < card.getChildCount(); i++) {
                TextView row = (TextView) card.getChildAt(i);
                theme.styleText(row, 15, 500, "onSurface");
                row.setPadding(theme.px(12), 0, theme.px(12), 0);
                Drawable glyph = icon(theme, rows.get(i)[0], "onSurfaceVariant");
                if (glyph != null) glyph.setBounds(0, 0, theme.px(22), theme.px(22));
                row.setCompoundDrawablesRelative(glyph, null, null, null);
                row.setCompoundDrawablePadding(theme.px(14));
            }
        }
    }

    /** A card centered over a dimmed kiosk. Tapping outside the card closes it. */
    private final class Full implements OverlayFactory {
        @Override public View create(Context context, KsTheme theme) {
            FrameLayout root = new FrameLayout(context);
            root.setOnClickListener(view -> tap(OverlayDemo.this::hideFull));
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            // Taps on the card stay on the card.
            card.setClickable(true);
            TextView title = new TextView(context);
            TextView body = new TextView(context);
            Button close = new Button(context);
            title.setText("Hello World");
            body.setText("This full screen overlay is a native Android view. It takes every tap until you close it with the button, a tap outside the card or the back button.");
            close.setText("Close");
            close.setOnClickListener(view -> tap(OverlayDemo.this::hideFull));
            card.addView(title);
            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bodyParams.topMargin = theme.px(12);
            card.addView(body, bodyParams);
            LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            closeParams.topMargin = theme.px(24);
            closeParams.gravity = Gravity.END;
            card.addView(close, closeParams);
            int width = Math.min(theme.px(420), context.getResources().getDisplayMetrics().widthPixels - theme.px(48));
            FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            root.addView(card, cardParams);
            onThemeChanged(root, theme);
            return root;
        }

        @Override public void onThemeChanged(View view, KsTheme theme) {
            FrameLayout root = (FrameLayout) view;
            root.setBackgroundColor(Color.argb(160, 0, 0, 0));
            LinearLayout card = (LinearLayout) root.getChildAt(0);
            card.setBackground(theme.card());
            card.setPadding(theme.px(theme.inset()), theme.px(theme.inset()), theme.px(theme.inset()), theme.px(theme.inset()));
            theme.styleText((TextView) card.getChildAt(0), 22, 600, "onSurface");
            theme.styleText((TextView) card.getChildAt(1), 15, 400, "onSurfaceVariant");
            theme.stylePill((TextView) card.getChildAt(2), true);
        }
    }

    /** A breathing glow along the screen edges in the theme's accent colors. */
    private static final class Glow implements OverlayFactory {
        @Override public View create(Context context, KsTheme theme) {
            return new GlowView(context, theme);
        }

        @Override public void onThemeChanged(View view, KsTheme theme) {
            ((GlowView) view).theme = theme;
            view.invalidate();
        }
    }

    private static final class GlowView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        KsTheme theme;

        GlowView(Context context, KsTheme theme) {
            super(context);
            this.theme = theme;
            paint.setStyle(Paint.Style.STROKE);
        }

        @Override protected void onDraw(Canvas canvas) {
            // One breath every three seconds, cycling primary into tertiary.
            double phase = (SystemClock.uptimeMillis() % 3000) / 3000.0;
            float breath = (float) (0.5 - 0.5 * Math.cos(phase * Math.PI * 2));
            int color = blend(theme.color("primary"), theme.color("tertiary"), breath);
            float radius = theme.px(theme.radiusCard());
            // Wide faint strokes under narrow strong ones read as a soft glow
            // without a blur, which hardware canvases skip on older Android.
            for (int ring = 6; ring >= 1; ring--) {
                float width = theme.px(ring * 6);
                paint.setStrokeWidth(width);
                paint.setColor(Color.argb((int) ((40 + 140 * breath) / ring), Color.red(color), Color.green(color), Color.blue(color)));
                bounds.set(0, 0, getWidth(), getHeight());
                canvas.drawRoundRect(bounds, radius, radius, paint);
            }
            postInvalidateOnAnimation();
        }

        private static int blend(int from, int to, float amount) {
            return Color.rgb(
                (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * amount),
                (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * amount),
                (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount));
        }
    }
}
