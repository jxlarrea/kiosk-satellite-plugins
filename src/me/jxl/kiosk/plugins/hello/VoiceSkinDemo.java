// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import me.jxl.kiosk.plugins.KsTheme;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * A voice skin. Pick Hello World in Voice Satellite > Appearance > Skin and
 * KS stops drawing its own overlay. This one draws the turn instead: a ring
 * that swells with the voice, an arc that turns while the assistant thinks
 * and the conversation under it. Full screen it dims the kiosk. Docked it
 * is a small bubble at the bottom that leaves the dashboard in view. It
 * draws only while its skin is picked, and every touch goes through. It
 * follows Voice Satellite's Appearance settings: Theme, Background, Text
 * size and Reactive activity bar.
 */
final class VoiceSkinDemo {
    static final String KEY = "voice_skin";
    /** Where the docked bubble sits above the bottom edge, in dp. */
    static final int DOCK_INSET = 24;
    static final String[] EVENTS = {"voice.skin", "voice.overlay", "voice.level", "voice.text", "voice.appearance"};
    /** The full screen backdrop's opacity when Background is left at Skin default. */
    static final float DEFAULT_BACKGROUND = 0.8f;
    private PluginHost host;
    private int generation;
    private boolean selected;
    /** A voice.skin event arrived, which is newer than the read. */
    private boolean skinHeard;
    private String overlay = "hidden";
    /** What it draws: null, "full" or "docked". */
    private String drawn;
    /** Read by the view on the main thread. */
    private volatile float level;
    private volatile String command = "";
    private volatile String answer = "";
    private volatile String tool = "";
    /** Appearance settings, read by the view on the main thread. */
    private volatile String theme = "auto";
    /** Null for Skin default. */
    private volatile Float background;
    private volatile float textScale = 1;
    private volatile boolean reactive = true;
    private boolean appearanceHeard;
    /** The showing view, so new words can reach it. Touched on the main thread only. */
    private SkinView view;

    synchronized void start(PluginHost host) {
        this.host = host;
        int current = ++generation;
        try {
            for (String event : EVENTS) host.subscribe(event);
        } catch (IllegalArgumentException | UnsupportedOperationException olderHost) {
            // Kiosk Satellite versions without voice skins never list this one.
            return;
        }
        try {
            read(current);
        } catch (UnsupportedOperationException olderHost) {
            // Events alone still bring the skin up.
        }
    }

    private void read(int current) {
        // Events win over these reads: they are newer.
        host.executeCommand("getVoiceState", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
            synchronized (VoiceSkinDemo.this) {
                if (current != generation || !ok || !(data instanceof Map)) return;
                Map<?, ?> state = (Map<?, ?>) data;
                if (!skinHeard) selected = Boolean.TRUE.equals(state.get("skinSelected"));
                if ("hidden".equals(overlay) && state.get("overlay") instanceof String) overlay = (String) state.get("overlay");
                update();
            }
        });
        host.executeCommand("getVoiceAppearance", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
            synchronized (VoiceSkinDemo.this) {
                if (current == generation && ok && data instanceof Map && !appearanceHeard) appearance((Map<?, ?>) data);
            }
        });
        host.executeCommand("getVoiceText", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
            synchronized (VoiceSkinDemo.this) {
                if (current == generation && ok && data instanceof Map && command.isEmpty() && answer.isEmpty()) words((Map<?, ?>) data);
            }
        });
    }

    synchronized void onEvent(String event, Map<String, Object> payload) {
        if (host == null) return;
        if ("ks.voice.skin".equals(event)) {
            selected = Boolean.TRUE.equals(payload.get("selected"));
            skinHeard = true;
        } else if ("ks.voice.overlay".equals(event)) {
            overlay = String.valueOf(payload.get("overlay"));
        } else if ("ks.voice.level".equals(event)) {
            Object value = payload.get("level");
            level = value instanceof Number ? ((Number) value).floatValue() : 0;
            return;
        } else if ("ks.voice.text".equals(event)) {
            words(payload);
            return;
        } else if ("ks.voice.appearance".equals(event)) {
            appearanceHeard = true;
            appearance(payload);
            return;
        } else {
            return;
        }
        update();
    }

    /** Whether the skin draws now, and how: null, "full" or "docked". */
    synchronized String drawn() { return drawn; }

    /** The Appearance settings as the skin reads them: theme, background, text scale and reactive. */
    synchronized Object[] appearance() { return new Object[] {theme, background, textScale, reactive}; }

    private void appearance(Map<?, ?> settings) {
        theme = settings.get("theme") instanceof String ? (String) settings.get("theme") : "auto";
        background = settings.get("background") instanceof Number ? ((Number) settings.get("background")).floatValue() : null;
        textScale = settings.get("textScale") instanceof Number ? ((Number) settings.get("textScale")).floatValue() : 1;
        reactive = !Boolean.FALSE.equals(settings.get("reactive"));
        SkinView current = view;
        if (current != null) current.post(current::restyle);
    }

    private void words(Map<?, ?> text) {
        command = text.get("command") instanceof String ? (String) text.get("command") : "";
        answer = text.get("answer") instanceof String ? (String) text.get("answer") : "";
        Object tools = text.get("tools");
        tool = tools instanceof List && !((List<?>) tools).isEmpty() ? String.valueOf(((List<?>) tools).get(((List<?>) tools).size() - 1)) : "";
        SkinView current = view;
        if (current != null) current.post(current::refresh);
    }

    /**
     * Shows the skin while it is picked and the overlay is up, in the look
     * that matches it. KS draws nothing of its own under a plugin skin,
     * docked included, so both looks are the plugin's to draw.
     */
    private void update() {
        String want = selected && ("full".equals(overlay) || "docked".equals(overlay)) ? overlay : null;
        if (want == null ? drawn == null : want.equals(drawn)) return;
        drawn = want;
        if (want == null) {
            host.hideOverlay(KEY);
            level = 0;
        } else if ("full".equals(want)) {
            host.showOverlay(KEY, OverlayDemo.animated(OverlaySpec.fullScreen().onTop(true).touchable(false).closeOnBack(false), OverlaySpec.FADE, OverlaySpec.FADE, 300), new Skin(false));
        } else {
            // A bubble at the bottom. Touches reach the dashboard around and under it.
            host.showOverlay(KEY, OverlayDemo.animated(OverlaySpec.at(OverlaySpec.BOTTOM).inset(DOCK_INSET).onTop(true).touchable(false).closeOnBack(false), OverlaySpec.SLIDE, OverlaySpec.SLIDE, 250), new Skin(true));
        }
    }

    synchronized void stop() {
        host = null;
        generation++;
        selected = false;
        skinHeard = false;
        overlay = "hidden";
        drawn = null;
        level = 0;
        command = answer = tool = "";
        theme = "auto";
        background = null;
        textScale = 1;
        reactive = true;
        appearanceHeard = false;
    }

    private final class Skin implements OverlayFactory {
        private final boolean docked;

        Skin(boolean docked) { this.docked = docked; }

        @Override public View create(Context context, KsTheme theme) {
            SkinView skin = new SkinView(context, theme, docked);
            view = skin;
            return skin;
        }

        @Override public void onThemeChanged(View view, KsTheme theme) {
            ((SkinView) view).restyle(theme);
        }

        @Override public void onDestroy(View destroyed) {
            if (view == destroyed) view = null;
        }
    }

    /**
     * Full screen: a dim backdrop, the ring and the conversation under it.
     * Docked: a card with a small ring beside the conversation.
     */
    private final class SkinView extends FrameLayout {
        private final boolean docked;
        private final Ring ring;
        private final TextView said;
        private final TextView answered;
        private final LinearLayout card;
        private KsTheme ksTheme;

        SkinView(Context context, KsTheme theme, boolean docked) {
            super(context);
            this.ksTheme = theme;
            this.docked = docked;
            ring = new Ring(context);
            said = new TextView(context);
            answered = new TextView(context);
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            if (docked) {
                card = new LinearLayout(context);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                int ringSize = theme.px(64);
                card.addView(ring, new LinearLayout.LayoutParams(ringSize, ringSize));
                LinearLayout words = new LinearLayout(context);
                words.setOrientation(LinearLayout.VERTICAL);
                answered.setMaxLines(3);
                answered.setEllipsize(android.text.TextUtils.TruncateAt.END);
                words.addView(said);
                words.addView(answered);
                LinearLayout.LayoutParams wordsParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                wordsParams.leftMargin = theme.px(16);
                card.addView(words, wordsParams);
                int width = Math.min(theme.px(560), screenWidth - theme.px(48));
                addView(card, new FrameLayout.LayoutParams(width, LayoutParams.WRAP_CONTENT));
            } else {
                card = null;
                said.setGravity(Gravity.CENTER);
                answered.setGravity(Gravity.CENTER);
                LinearLayout column = new LinearLayout(context);
                column.setOrientation(LinearLayout.VERTICAL);
                column.setGravity(Gravity.CENTER);
                int size = Math.min(screenWidth, context.getResources().getDisplayMetrics().heightPixels) / 3;
                column.addView(ring, new LinearLayout.LayoutParams(size, size));
                LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                line.topMargin = theme.px(24);
                column.addView(said, line);
                LinearLayout.LayoutParams answerLine = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                answerLine.topMargin = theme.px(8);
                column.addView(answered, answerLine);
                int side = theme.px(48);
                column.setPadding(side, 0, side, 0);
                addView(column, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            }
            restyle(theme);
            refresh();
        }

        void restyle(KsTheme next) {
            ksTheme = next;
            restyle();
        }

        /**
         * Theme picks the palette: Auto follows the KS theme, Light and Dark
         * force theirs. Background sets the full screen backdrop's opacity
         * and Text size scales every line.
         */
        void restyle() {
            boolean dark = "dark".equals(theme) || (!"light".equals(theme) && ksTheme.isDark());
            int backdrop = dark ? 0xFF121316 : 0xFFF5F4F2;
            int text = dark ? 0xFFE8EAED : 0xFF212327;
            int muted = dark ? 0xFFB6BABF : 0xFF5D6066;
            float scale = textScale;
            if (docked) {
                GradientDrawable bubble = new GradientDrawable();
                bubble.setColor(dark ? 0xFF2A2B2F : 0xFFFFFFFF);
                bubble.setCornerRadius(ksTheme.px(ksTheme.radiusCard()));
                bubble.setStroke(Math.max(1, ksTheme.px(1) / 2), dark ? 0x33FFFFFF : 0x1F000000);
                card.setBackground(bubble);
                int pad = ksTheme.px(16);
                card.setPadding(pad, pad, ksTheme.px(20), pad);
                ksTheme.styleText(said, 14 * scale, 400, "onSurfaceVariant");
                ksTheme.styleText(answered, 17 * scale, 500, "onSurface");
            } else {
                float opacity = background != null ? background : DEFAULT_BACKGROUND;
                setBackgroundColor(Color.argb(Math.round(255 * opacity), Color.red(backdrop), Color.green(backdrop), Color.blue(backdrop)));
                ksTheme.styleText(said, 20 * scale, 400, "onSurfaceVariant");
                ksTheme.styleText(answered, 28 * scale, 500, "onSurface");
            }
            said.setTextColor(muted);
            answered.setTextColor(text);
            ring.invalidate();
        }

        void refresh() {
            said.setText(command);
            said.setVisibility(command.isEmpty() ? GONE : VISIBLE);
            answered.setText(answer.isEmpty() ? tool : answer);
            answered.setVisibility(answer.isEmpty() && tool.isEmpty() ? GONE : VISIBLE);
        }

        /** The ring, eased toward the latest level so 30 readings a second still move smoothly. */
        private final class Ring extends View {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF arc = new RectF();
            private float shown;

            Ring(Context context) {
                super(context);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
            }

            @Override protected void onDraw(Canvas canvas) {
                // With Reactive activity bar off the ring only breathes. KS sends 0 then too.
                shown += ((reactive ? level : 0) - shown) * 0.25f;
                float cx = getWidth() / 2f, cy = getHeight() / 2f;
                float max = Math.min(cx, cy);
                long now = SystemClock.uptimeMillis();
                // Breathes gently at rest, swells with the voice.
                float breath = (float) (0.5 - 0.5 * Math.cos((now % 3000) / 3000.0 * Math.PI * 2));
                // Docked, the ring leaves room for the thinking arc inside its small box.
                float radius = docked ? max * (0.5f + 0.04f * breath + 0.25f * shown) : max * (0.55f + 0.05f * breath + 0.35f * shown);
                int primary = ksTheme.color("primary");
                // The docked ring is small, so its glow is too.
                float scale = docked ? 0.3f : 1f;
                for (int glow = 4; glow >= 1; glow--) {
                    paint.setStrokeWidth(ksTheme.px(4 + glow * 6) * scale);
                    paint.setColor(Color.argb(Math.round((50 + 150 * shown) / glow), Color.red(primary), Color.green(primary), Color.blue(primary)));
                    canvas.drawCircle(cx, cy, radius, paint);
                }
                // Something was said and no answer is in yet: the assistant thinks.
                if (!command.isEmpty() && answer.isEmpty() && tool.isEmpty() && shown < 0.05f) {
                    paint.setStrokeWidth(ksTheme.px(6) * scale);
                    paint.setColor(ksTheme.color("tertiary"));
                    float r = radius + ksTheme.px(18) * scale;
                    arc.set(cx - r, cy - r, cx + r, cy + r);
                    canvas.drawArc(arc, (now % 1200) / 1200f * 360, 90, false, paint);
                }
                postInvalidateOnAnimation();
            }
        }
    }
}
