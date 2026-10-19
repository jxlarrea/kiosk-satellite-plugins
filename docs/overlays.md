# Native overlays

SDK 1 plugins with the `overlay` capability can draw their own Android views over the kiosk. Use them for a control bar, a volume slider, an alert banner, a full screen panel or an animated full screen visual like the Voice Satellite overlay. KS hosts each view inside its own screen, so the menu, the screensaver and Lockdown Mode cover it the way they cover the dashboard, and it disappears when KS goes to the background. A `KsTheme` passes the KS colors, shapes and typeface, so the overlay can look like the rest of the app.

## Show an overlay

Call `showOverlay(String key, OverlaySpec spec, OverlayFactory factory)` on the host. Declare `"overlay"` in the manifest's `capabilities` and keep `apiVersion: 1`.

```java
host.showOverlay("bar", OverlaySpec.at(OverlaySpec.TOP).inset(16), new OverlayFactory() {
    @Override public View create(Context context, KsTheme theme) {
        TextView label = new TextView(context);
        label.setText("Hello");
        label.setBackground(theme.card());
        label.setPadding(theme.px(20), theme.px(12), theme.px(20), theme.px(12));
        theme.styleText(label, 15, 500, "onSurface");
        return label;
    }
});
```

Each call replaces the overlay at that key with a fresh view from the new factory. Call `hideOverlay(String key)` to remove it. Hiding an unknown key does nothing.

| Field | Contract |
| --- | --- |
| `key` | Stable overlay ID matching `[a-z][a-z0-9_]{0,39}`. Scoped to the plugin |
| `spec` | Placement, size and behavior. See below |
| `factory` | Builds the view. KS calls it on the Android main thread |

### Threads and timing

`showOverlay` and `hideOverlay` never block and are safe to call from any thread, the main thread and click listeners included. Each records the change and returns. KS builds the view afterward, so `create` and `onDestroy` never run inside the call, even when it comes from the main thread. Both always run on the main thread.

- A `hideOverlay` that follows `showOverlay` before the view is built cancels it, and `create` never runs for it.
- When you replace an overlay, the new view's `create` and the old view's `onDestroy` both run later. Do not rely on their order. Keep state that both views share outside the view.
- The view appears once KS has laid it out, which takes at least one frame after the call.

### Reuse overlays

Each overlay is its own Android view hosted inside KS's Flutter screen, and creating one is the expensive part. A fast tablet shows a new overlay within a frame or two. A low-power panel can take a second or more, and much longer while a busy dashboard keeps it drawing. Replacing an overlay at the same key creates a new view too. Changes inside an existing view, such as text, colors or what is visible, are as quick as in any Android app.

- Keep one long-lived overlay for anything that changes often, like a bar that collapses into a tab, and change its contents instead of replacing it.
- Show a separate overlay for something short-lived, like a menu, and keep it small.
- Avoid calling `showOverlay` on every state change.

## Placement and size

`OverlaySpec` describes where the overlay sits. Sizes and insets are in dp.

| Method | Contract |
| --- | --- |
| `OverlaySpec.at(anchor)` | A wrapped overlay at `TOP_LEFT`, `TOP`, `TOP_RIGHT`, `LEFT`, `CENTER`, `RIGHT`, `BOTTOM_LEFT`, `BOTTOM` or `BOTTOM_RIGHT` |
| `OverlaySpec.fullScreen()` | Covers the whole kiosk area |
| `size(width, height)` | `OverlaySpec.WRAP`, `OverlaySpec.FILL` or 1 to 4096 dp for each side. Both default to `WRAP` |
| `inset(dp)` | The same distance from every kiosk edge, 0 to 4096 dp. Defaults to 0 |
| `inset(left, top, right, bottom)` | A distance from each kiosk edge, 0 to 4096 dp. Use it to keep an overlay clear of a bar, for example a menu above a bottom bar with `inset(0, 0, 0, barHeight)` |
| `offset(x, y)` | Moves the overlay from its anchored place, -4096 to 4096 dp. Positive x moves right and positive y moves down. Defaults to 0, 0 |
| `closeOnOutsideTouch(boolean)` | A touch anywhere outside the overlay closes it. Defaults to off. See [Close on an outside touch](#close-on-an-outside-touch) |
| `passOutsideTouch(boolean)` | With `closeOnOutsideTouch`, the touch that closes the overlay also reaches what is under it. Defaults to off |
| `enter(transition)`, `exit(transition)` | How the overlay appears and leaves: `NONE`, `FADE`, `SLIDE` or `SCALE`. Both default to `NONE`. See [Transitions](#transitions) |
| `transitionDuration(ms)` | Length of both transitions, 50 to 1000 ms. Defaults to 200 |
| `closeOnBack(boolean)` | Whether back closes the overlay. Defaults to on |
| `onTop(boolean)` | Draw where the KS voice overlay does, over the screensaver, the menu and camera views. Defaults to off |
| `touchable(boolean)` | Off makes the overlay visual only, and every touch passes through to whatever is under it. Defaults to on |

A `WRAP` side follows the view's measured size and updates when the view asks for a new layout. KS measures it against the kiosk area less the insets on that axis. A `FILL` side takes the whole area less the insets. A fixed size is capped at the available area.

Overlays follow the kiosk's **Scale UI** setting like the rest of Kiosk Satellite. The context passed to `create` has a density that includes it, so dp and sp lengths in views, layouts and `KsTheme` grow with it. KS scales the sizes, insets and offsets in `OverlaySpec` and the space from `setContentInsets` by the same amount. Convert pixels to dp with the view's own density and everything lines up at every scale. A new scale builds the view again.

The offset applies after the anchor, and KS keeps the overlay inside the area its insets leave, so a large offset stops at the edge instead of pushing the overlay off screen. With `TOP_LEFT` and no insets, the offset is the overlay's position from the top left of the kiosk area. To open a menu next to a view in another overlay, read that view's position with `getLocationOnScreen`, divide by the view's density and offset from the matching anchor. Hello World's **More** menu opens under its button this way.

Transparent and translucent pixels show what is under the overlay, so a full screen view can dim the kiosk or draw effects over it.

A touchable overlay takes every touch inside its rectangle, including transparent parts of the view. Touches outside it reach the dashboard. A touchable full screen overlay therefore blocks the dashboard until it closes. To float a few controls over the dashboard, show several small overlays instead of one transparent full screen view. A full screen visual that should not block anything, such as a voice animation or an ambient effect, uses `touchable(false)`.

## The view

`OverlayFactory` has one required method and two optional callbacks. All of them run on the Android main thread.

| Method | Contract |
| --- | --- |
| `create(context, theme)` | Build and return the view. KS may call it again for the same overlay, for example after its Activity restarts |
| `onThemeChanged(view, theme)` | The KS theme changed, for example from light to dark. Restyle the view |
| `onDestroy(view)` | KS removed the view. Stop its animations and drop references to it |

Plugin callbacks such as `configure` and `onEvent` run on the plugin worker, not the main thread. Post view updates with `view.post(...)` or a main `Handler`. Keep a reference to the current view if you update it later, and clear it in `onDestroy`.

The view is drawn into a texture that KS composites with the rest of its screen. Regular views, layouts, animations and canvas drawing work. `SurfaceView`, `VideoView` and other views that render outside the view hierarchy fall back to a slower path and are not supported. Shadows from `setElevation` are clipped to the overlay's rectangle.

## Theme

`KsTheme` mirrors the KS theme in native units. KS builds a new one for each view and for every theme change.

| Method | Contract |
| --- | --- |
| `color(role)` | ARGB color by Material 3 role name, such as `surface`, `onSurface`, `onSurfaceVariant`, `surfaceContainerHigh`, `primary`, `onPrimary`, `primaryContainer`, `secondary`, `tertiary`, `error`, `outline` and `outlineVariant`, plus `success`. Unknown roles throw `IllegalArgumentException` |
| `colors()` | Every role this KS version provides |
| `isDark()` | Whether KS is in its dark theme |
| `px(dp)` | Pixels for a length in dp |
| `radiusCard()`, `radiusRow()`, `radiusControl()` | Corner radii in dp: 24 for cards and dialogs, 14 for rows, 12 for inputs |
| `inset()`, `cardGap()` | Card padding and the gap between cards, in dp |
| `typeface(weight)` | Rubik, the KS typeface, at a weight from 100 to 900 |
| `card()` | A background like the one KS toasts use over the dashboard: `surfaceContainerHigh`, the card radius and a hairline outline |
| `styleText(view, sp, weight, role)` | Applies the typeface, size and a color role to a `TextView` |
| `stylePill(view, filled)` | Turns a `TextView` or `Button` into a KS pill button. Filled uses `primary`, otherwise it is a quiet text button |
| `styleSlider(seekBar)` | Styles a `SeekBar` like the KS sliders: a 4 dp track filled in `primary` over `surfaceContainerHighest`, a 20 dp `primary` thumb and a 44 dp touch target |
| `icon(name, role)` | A Material Design Icon from KS's own set, such as `mdi:garage`, as a `Drawable` in a color role. It is 24 dp unless its bounds say otherwise, and `setTint` recolors it. Null for an unknown name |
| `iconPath(name)` | The icon's outline as a `Path` on a 24 by 24 grid, for drawing it yourself. Null for an unknown name |

KS ships every Material Design Icon that Home Assistant uses, renamed icons included, so a plugin no longer needs to bundle an icon font. Lookups are quick enough for the main thread, and KS keeps recent icons.

The context passed to `create` carries the matching Android `DeviceDefault` light or dark theme, so stock widgets start close to KS. The theme does not change that context later, so restyle in `onThemeChanged`.

## Keep the dashboard clear

An overlay floats over the dashboard, so a bar along an edge covers the cards under it. Call `host.setContentInsets(left, top, right, bottom)` to reserve that space, in dp from each edge, 0 to 4096. Home Assistant pads its views, dialogs, toasts and view buttons by it, through the same safe-area values it uses for a phone's notch, so the first or last cards sit clear of the bar and the dashboard's background still runs under it. Custom pages read the same space from `--ks-content-inset-*` CSS variables. The WebView itself keeps its size.

```java
// A 64 dp bar 12 dp above the bottom edge.
host.setContentInsets(0, 0, 0, 76);
```

- Reserve the bar's full depth, its inset from the edge included. All zero gives the space back.
- Each call replaces the plugin's previous insets. With several plugins, each edge keeps the largest.
- KS clears them when the session ends, so a disabled or stopped plugin never leaves a gap.
- The page lays itself out again when the insets change. Keep the space while a bar tucks away for a moment, rather than freeing and reserving it on every change.
- Requires `overlay`. Kiosk Satellite versions without it throw `UnsupportedOperationException` or `NoSuchMethodError`.

Hello World's top bar reserves its depth whenever its height changes and frees it when hidden.

## Close on an outside touch

With `closeOnOutsideTouch(true)`, a touch anywhere outside the overlay closes it, the way a popup menu closes, and KS sends `onEvent("overlay.closed", {"key": key})`. By default that touch only closes the overlay, so a tap meant to dismiss a menu does not also press a dashboard button under it. Add `passOutsideTouch(true)` to let it through as well. Touches inside the overlay reach it as usual, and other plugin overlays above it are not "outside".

```java
host.showOverlay("menu", OverlaySpec.at(OverlaySpec.BOTTOM_LEFT)
    .inset(0, 0, 0, barHeight)
    .offset(itemX, 0)
    .closeOnOutsideTouch(true)
    .enter(OverlaySpec.SCALE).exit(OverlaySpec.FADE), factory);
```

## Transitions

`enter` animates the overlay in once KS has laid it out, and `exit` animates it out when it is hidden, replaced or closed by back or an outside touch. During the exit the view stays alive and takes no touches, and `onDestroy` runs when the exit ends. Replacing an overlay at the same key plays the old view's exit and the new view's enter together, so give the replacement `NONE` to swap its content without a visible change.

| Transition | Effect |
| --- | --- |
| `NONE` | Shows or removes the overlay at once |
| `FADE` | Fades in or out |
| `SLIDE` | Slides in from, or out to, the edge of its anchor while it fades. A centered overlay rises a little |
| `SCALE` | Grows from, or shrinks to, a slightly smaller size while it fades. Suits a centered card or a menu |

Overlays of a session that ends, such as a disabled plugin, leave at once without their exit.

## Back and events

Back closes the topmost overlay with `closeOnBack` on, after any floating window. KS removes it and sends `onEvent("overlay.closed", {"key": key})`. An overlay with `closeOnBack(false)` is left alone and back does what it normally does. Use that for bars that should stay up. `hideOverlay` does not send `overlay.closed`.

While Lockdown Mode is on, back never closes an overlay.

## Layering

Overlays sit above the dashboard and its web overlays and below the Sendspin player, the menu, the screensaver, notifications and Lockdown Mode. The menu slides them aside with the dashboard. Floating windows sit above them. Within those layers, a new or replaced overlay goes on top.

An overlay with `onTop(true)` moves to the slot of the KS voice overlay, over the screensaver, the menu and camera views. Notifications, intercom call cards, announcements, the voice overlay itself, the screensaver's black cover and Lockdown Mode still cover it. During a voice turn it draws over the black cover too, so a [voice skin](voice-skins.md) shows on a blanked screen. Use it for visuals that must show whatever the kiosk is doing. It does not stop or dismiss the screensaver.

## Limits and lifetime

- Each plugin session can own at most four overlays. Replacing an existing key does not add another overlay.
- At most eight `showOverlay` calls are accepted per plugin per one-second rate window. Invalid input throws `IllegalArgumentException`. A stopped session or exhausted rate limit throws `IllegalStateException`.
- Disabling, uninstalling, updating or stopping the plugin, stopping the app or turning off **Enable Plugins** removes the session's overlays. Overlays are never persisted or included in fleet sync.
- KS revokes the host before calling `stop`. A `hideOverlay` call there is ignored.
- Kiosk Satellite versions without native overlays throw `UnsupportedOperationException`. Catch it if the plugin should keep working there.
- `OverlaySpec` and `KsTheme` come from Kiosk Satellite at runtime, so a version without per-edge insets, offsets, outside touches, transitions or icons throws `NoSuchMethodError` when you call one. Catch `LinkageError` around those calls to fall back, as Hello World's `animated` and `icon` helpers do.
- Remote Admin screenshots and the `screenshot` command copy the kiosk's window, and an overlay can be missing from them or show an earlier state. To check what an overlay looks like on the panel, use `adb exec-out screencap -p > screen.png`.

## Errors in view code

An exception thrown from `create`, `onThemeChanged` or `onDestroy` disables the plugin, like an error in any other callback. Click listeners, `onDraw`, `onTouchEvent` and runnables you post run outside those guards, so an exception there ends the app. KS switches the plugin off before the crash, and the app comes back without it. Catch exceptions in listeners, and expect host calls from the main thread to throw `IllegalStateException` once the session has ended.

## Hello World demo

Hello World's actions show each kind. **Show top bar** slides a wrapped pill in at the top of the kiosk with a greeting counter, a slider with its value, a filled **Say hello** button, a quiet **Full screen** button and a **More** button drawn with `KsTheme.icon`. **More** opens a menu under the bar, centered on the button with per-edge insets and an offset, which scales in and closes on a tap anywhere else. While it shows, Home Assistant's view starts below it. Back leaves the bar alone, and **Hide top bar** slides it away and gives the space back. **Full screen** and the **Show full screen overlay** action open a card centered over a dimmed kiosk. Back, **Close** or a tap outside the card closes it. **Show edge glow** draws a breathing glow along the screen edges with `onTop(true)` and `touchable(false)`: it stays over the screensaver and camera views while the dashboard keeps answering every tap. Back or **Hide edge glow** removes it. Switch KS between its light and dark themes to see them restyle. See [OverlayDemo.java](../src/me/jxl/kiosk/plugins/hello/OverlayDemo.java).

## Updating an existing plugin repository

1. Update Kiosk Satellite to a build that includes native overlays.
2. Copy the current files from [`sdk/src/me/jxl/kiosk/plugins/`](../sdk/src/me/jxl/kiosk/plugins) into the same directory in your plugin repository, including `OverlaySpec.java`, `OverlayFactory.java` and `KsTheme.java`. Do not package the SDK classes in your plugin ZIP.
3. The overlay classes use Android views, so the SDK now compiles against the Android platform. Copy the current `tools/build.py` and `tools/test.py`, or add the platform's `android.jar` to the classpath of your own SDK and test compile steps.
4. Add `"overlay"` to `capabilities` and keep `apiVersion: 1`.
5. Test the local ZIP through **Plugin Manager > Developer Tools > Install from ZIP**, then publish a release as described in [Creating plugins](creating-plugins.md).
