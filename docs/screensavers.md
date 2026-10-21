# Plugin screensavers

A plugin screensaver replaces the rendered content inside the stock KS screensaver. KS still owns activation, idle timeout, schedules, brightness, motion and other wake triggers, touch dismissal, screen-off timing, notification brightness, widgets, At a Glance, pixel shift and Now Playing layouts. The plugin does not open its own overlay or implement its own idle timer.

## Try the DVD demo

Enable **Hello World** in Plugin Manager. Open **Screensaver > Screensaver mode** and choose **DVD Logo (Hello World)**. You can also choose it in a screensaver schedule entry. Start the screensaver from the kiosk menu or wait for the configured idle timeout.

In **Plugin Manager > Hello World > Screensaver demo**, adjust **Logo size** from 50% to 200% or change **Logo color** and **Background color**. The default 100% keeps the original size. All three save automatically and refresh an active renderer. The logo bounces around its available viewport, including the smaller viewport beside Now Playing. It keeps the selected color on each bounce. The greeting, charts and optional Shizuku demo work independently.

## Register a renderer

Declare `"screensaver"` in the manifest's `capabilities` and keep `apiVersion: 1`. Use the current SDK interfaces from this repository.

Bundle your renderer under `assets/`:

```text
assets/
  dvd/
    index.html
    dvd.css
    dvd.js
    dvd.svg
```

```java
Map<String, Object> options = new LinkedHashMap<>();
options.put("logoColor", "#00D4FF");
host.publishScreensaverAsset("dvd", "DVD Logo", "dvd/index.html", options);
host.removeScreensaver("dvd");
```

`publishScreensaverAsset(String key, String title, String entry, Map<String, Object> data)` registers an HTML file relative to `assets/`. Relative stylesheet, script, image, font and data URLs resolve normally. KS serves those files directly from the verified package through Android's local asset loader. They are read when the WebView requests them, not copied into a Flutter message or embedded in the HTML. No local HTTP server or network connection is involved.

The entry path must end in `.html` or `.htm`. Asset path segments use letters, digits, underscores, hyphens and periods, start with a letter or digit and cannot be `.` or `..`. Paths have at most 240 characters with at most 128 per segment. Symlinks, absolute paths and encoded path separators are rejected. The build tool includes `assets/` automatically and validates these rules.

The data map carries string, boolean, finite number or null values under identifier keys. The map is serialized once and detached from later changes. Read it in the renderer using:

```javascript
const options = JSON.parse(decodeURIComponent(location.hash.slice(1) || '%7B%7D'));
```

The fragment stays outside asset request paths. It is configuration for this rendering instance, not a bridge back to KS. Publishing changed options recreates the document, so settings apply immediately. Do not publish unchanged options on every sample or animation frame.

The existing `publishScreensaver(String key, String title, String html)` method remains available for small inline documents. Its 512 KiB limit applies only to that inline HTML argument. Packaged HTML and asset files do not have that per-document limit.

None of the publish methods selects or starts a screensaver. To select one, see [Switch screensavers](#switch-screensavers). Publish during `start` and again when rendering settings or content change. KS lists the renderer as **Title (Plugin Name)** in both the mode picker and schedule editor. Multiple plugins can use the same key.

`removeScreensaver(String key)` withdraws that renderer. Every renderer is automatically removed when its plugin session ends, including disable, uninstall, failed startup and replacement during an update. Calls through a revoked host fail.

The stored mode is `plugin:PLUGIN_ID:KEY`. Keep IDs and keys stable across releases so selections and schedules reconnect when the plugin is enabled again. KS preserves the selected mode when its renderer is unavailable and shows a black background beneath any configured stock overlays. It keeps all screensaver policies active and resumes rendering when that key is published again. It does not wake the kiosk or rewrite its settings.

| Field | Limit |
| --- | --- |
| Key | Lowercase letter followed by up to 39 lowercase letters, digits or underscores |
| Title | Nonblank, up to 80 characters |
| Inline HTML argument | Nonblank, up to 512 KiB in UTF-8. Does not apply to packaged HTML or assets |
| Asset rendering data | Scalar JSON values, up to 32 KiB in UTF-8. Identifier keys up to 40 characters |
| Package | Existing 4 MiB compressed and expanded limits, shared by code, manifest, license and assets |
| Package file count | Up to 512 files |
| Renderers | Up to four per plugin session |
| Changes | Up to four publish or remove calls per second per session |

## Draw it natively

A plugin can draw its screensaver with an Android view instead of HTML. Pass an `OverlayFactory`, the same one [native overlays](overlays.md) use:

```java
host.publishScreensaverView("board", "PC sensors", new OverlayFactory() {
    @Override public View create(Context context, KsTheme theme) {
        BoardView view = new BoardView(context, theme);
        views.add(view);
        return view;
    }
    @Override public void onDestroy(View view) {
        views.remove(view);
    }
});
```

`publishScreensaverView(String key, String title, OverlayFactory factory)` shares the keys, titles and limits of the HTML renderers. Publishing a view under a key replaces an HTML renderer under it, and the other way around. The renderer shows in the mode picker the same way, as **Title (Plugin Name)**.

KS builds the view on the main thread each time the screensaver shows it and fills the screensaver area with it, on black. The context and `KsTheme` carry the kiosk's UI scale, colors, Rubik and the Material Design Icons `KsTheme.icon` and `KsTheme.iconPath` draw, so the view can use the same `mdi:` names as the `icon` setting. Stock widgets, At a Glance and pixel shift work the same as for HTML renderers. Touches stay with KS.

`onDestroy` means the screensaver is gone: dismissed, switched to another mode or covered by full-screen Now Playing. Stop updating the view then. KS calls `create` again the next time, and also after its Activity restarts or the UI scale changes, so keep the state outside the view. KS does not pause the view while the screen is off. A view that animates should stop when it is not shown, for example in `onWindowVisibilityChanged`. An exception from `create` or the view's code disables the plugin.

Update a native view from any thread with `view.post`. Unlike an HTML renderer, the plugin changes what the view shows without publishing again, so live readings cost nothing beyond drawing them. Kiosk Satellite versions without native screensavers throw `UnsupportedOperationException`. Catch it, along with `LinkageError`, if the plugin should still install there.

## Switch screensavers

Declare `screensaver.mode` to change which screensaver shows, for example to show the plugin's own while a game runs. The capability works on its own. It does not need `host.read` or `host.control`.

```java
host.executeCommand("setScreensaverMode", Collections.singletonMap("mode", "plugin:my-plugin:board"), (ok, data, error) -> {
    if (!ok) host.status(error, true);
});
// Later, to give the choice back:
host.executeCommand("clearScreensaverMode", Collections.emptyMap(), (ok, data, error) -> {});
```

| Command | Arguments and result |
| --- | --- |
| `getScreensaverMode` | `{}`. Returns `{mode, saved, schedule, override, active, modes}`. `mode` is the mode that shows now. `saved` is the **Screensaver mode** setting, `schedule` the mode of the active schedule entry or null and `override` the mode a plugin set or null. `active` is true while the screensaver shows. `modes` lists `{id, label}` for every stock mode the device offers, labeled in its language, and every published plugin screensaver |
| `setScreensaverMode` | `{mode: string}`. Shows `mode` in place of the schedule and the saved setting until the plugin clears it. `mode` must be an `id` from `modes`. An active screensaver switches right away |
| `clearScreensaverMode` | `{}`. Removes this plugin's override. The schedule or the saved setting applies again |

The override lives only while the plugin runs. KS does not save it, sync it through Fleet Management or show it in the ESPHome **Screensaver mode** select, and it removes it when the plugin stops. When several plugins set one, the latest wins until it clears its own. The override decides only which screensaver shows. KS still owns when it starts, so add `host.control` and call `startScreensaver` to start it right away.

## Rendering contract

Asset documents support HTML, CSS, JavaScript, SVG, canvas, images and fonts from the package. They load under a private virtual HTTPS origin for their plugin session. Relative URLs, JavaScript modules and same-origin fetches for bundled data work normally. KS checks asset digests when enabling the plugin and grants the WebView access only to that package's asset directory. An updated or restarted plugin gets a fresh origin.

External network requests, arbitrary local file access, navigation, popups and browser permissions remain blocked. The document has no KS JavaScript bridge, dashboard credentials or persistent WebView storage. The Java plugin itself remains trusted code running inside KS with app permissions. Inline documents retain their existing isolated frame and embedded-data behavior.

Use `requestAnimationFrame` for animation. Do not send each animation frame through `publishScreensaver`. Render against the current viewport and respond to resizing. Use viewport units or a `resize` listener for rotation and Now Playing layouts. Avoid fixed screen resolutions. The [DVD renderer](../assets/dvd/dvd.js) demonstrates a responsive SVG and elapsed-time animation with bounds checks.

KS creates the document only while that screensaver surface is visible. Replacing its HTML, asset entry or rendering options recreates the document. Dismissal, switching modes and full-screen Now Playing remove it. Screen-off and app-background transitions pause the individual WebView without pausing the dashboard's timers. A renderer crash leaves a black background for the remainder of that document's lifetime. Starting a new session or replacing the document creates a fresh renderer.

Input belongs to KS. Buttons and links inside the document do not receive taps. Stock dismissal settings apply to the whole surface. Stock widgets and At a Glance render above it and retain their schedule overrides. Stock pixel shift moves the rendered surface. Mode-specific settings for other screensavers, such as a stock photo folder or clock font, continue to configure those screensavers. Define your own content settings in the plugin manifest.

If native code needs to fetch content, perform that work asynchronously in the plugin and publish new rendering options or an inline document when it changes. Do not block SDK lifecycle callbacks. Optional `host.read` subscriptions can observe stock screensaver state through the existing [KS interaction API](ks-api.md). Rendering alone needs only the `screensaver` capability.

## Fleet management

Plugin packages, renderer documents and plugin settings remain local to each kiosk. A selected plugin mode and any schedule containing plugin modes are excluded from fleet pushes. A follower's local plugin mode or plugin schedule is preserved when a leader sends stock screensaver settings. Other stock screensaver settings continue following their normal fleet rules.

## Build and install

Run `python3 tools/test.py` and `python3 tools/build.py` with the Android SDK and Java configured as described in the [build guide](creating-plugins.md). Developer ZIP installation is for local testing. Publish plugins through GitHub Releases using the supplied workflow and publication checks, as described in the [installation guide](installing-plugins.md).
