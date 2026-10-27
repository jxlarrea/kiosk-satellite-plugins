# Creating plugins

SDK 1 lets a plugin display one floating window and its own native overlays, receive window events, expose commands and save settings. Start with Hello World and build a ZIP with `tools/build.py`.

## Build and check the SDK

The template includes the SDK interfaces, build tool and lifecycle tests. Copy the template into a separate repository for your plugin. Install Python 3, JDK 17 or newer and an Android SDK with a platform and build-tools. Set `ANDROID_HOME` and `JAVA_HOME` as needed.

```sh
python3 tools/test.py
python3 tools/build.py
python3 tools/check-sdk.py /path/to/kiosk-satellite
```

Release builds pass `--android-platform 35` to use the platform installed by the workflow, regardless of newer platforms already on the runner. Local builds can pass the same option or omit it to choose the highest installed numeric platform version, including dotted names such as `37.0`. Preview and extension directory names are skipped during automatic selection.

The SDK check compares this repository's interfaces and license with the application's copy. Keep the SDK as a compile-time dependency. The application supplies those interfaces at runtime. The [native overlay](overlays.md) classes use Android views, so the build and test tools compile the SDK against the Android platform.

The SDK, template, tooling and documentation use [Apache-2.0](../LICENSE). Contributors retain copyright to their work and contribute under the license of the component they change. Plugins may choose their own license and must retain any required third-party notices.

## Test a local build

For developer testing only, use **Plugin Manager > Developer Tools > Install from ZIP** on the kiosk or remote admin. Select the built ZIP from `dist/`, confirm that you trust the code and enable the installed plugin. The ZIP contains `kiosk-satellite-plugin.json`, `plugin.jar`, `LICENSE` and any bundled `assets/` files. The standalone release manifest and checksum file are only needed when publishing to GitHub.

Local packages use the same 4 MB size limit, manifest validation and DEX checks as release packages. New plugins start disabled. To test another build, install the replacement ZIP directly. KS automatically stops running plugins and restores their enabled state after the update. A normal update does not require an app restart. Compatible settings are retained. A local ZIP cannot replace a plugin installed from GitHub. Uninstall that plugin first, which also deletes its settings.

## Repository and release

Each public GitHub repository contains one plugin and these root files:

```text
README.md                     Documentation shown before installation
kiosk-satellite-plugin.json   The single plugin manifest
LICENSE                       Plugin license included in the package
src/                          Plugin source
```

Publish a release from the tagged source and let the included GitHub Actions workflow build and attach these three files from `dist/`:

```text
kiosk-satellite-plugin.json
<id>-<version>.zip
<id>-<version>.zip.sha256
```

Use `.github/workflows/build.yml` from this template. It builds on a published release or an explicit retry on an existing release tag, not on each commit. It compiles the tagged source on `ubuntu-24.04`, tests it and uploads with `${{ github.token }}`. The tag supplies the package version, optionally prefixed by `v`. The workflow passes it as `--version` to the builder, which uses it in both generated manifests and the ZIP filename. The source manifest keeps its local development version. When updating an existing workflow, also copy `tools/build.py`, `tools/android_sdk.py`, `tools/plugin_manifest.py` and `tools/plugin_assets.py` from the template so the builder supports the version argument. Keep all three assets from the same workflow build.

KS requires GitHub's uploader identity to be `github-actions[bot]` for all three assets and checks the ZIP against both GitHub's asset digest and the release checksum. A manually attached ZIP, manifest or checksum is rejected. Uploading through a personal access token is also rejected. ZIP installation is a developer escape hatch for local testing and does not count as a verified repository release.

This check establishes that GitHub Actions published the bytes. It does not cryptographically prove that the workflow compiled those bytes or ran on a GitHub-hosted runner. A repository owner can change workflow code, so users still need to review and trust the author. Full artifact attestation verification is not implemented. See GitHub's [release asset metadata](https://docs.github.com/en/rest/releases/assets) and [workflow token documentation](https://docs.github.com/en/actions/security-for-github-actions/security-guides/automatic-token-authentication).

The attached manifest is an exact copy of the generated manifest inside the ZIP. It describes the plugin without release URLs or checksums. The ZIP filename comes from its `id` and `version`. The checksum file contains one line in `sha256sum` format: the 64 lowercase hexadecimal hash, two spaces and the ZIP filename.

Commit the source, manifest and README before creating the release tag. Tags start with a letter or digit and contain only letters, digits, periods, underscores or hyphens, up to 151 characters. Publish the release as the latest stable release. KS uses GitHub's latest release endpoint. Drafts and prereleases are excluded.

KS reads the manifest and checksum attached to that release. It resolves the release tag to a commit and reads `README.md` at that commit, including the base for relative documentation links. Edits on the default branch do not change a released version's preview. The preview displays the manifest, compatibility and README without downloading or running plugin code.

A preview lasts 15 minutes. Installation downloads the selected release's ZIP, checks its required SHA-256 and compares every field of the packaged manifest with the reviewed manifest. A newer release published during review does not change the approved download. Repository metadata, release tag, commit and reviewed README are saved for offline use. README HTML is sanitized in remote admin. The device renders Markdown without executable HTML. Links and images require HTTPS.

The manifest is limited to 32 KB, the checksum file to 1 KB and the README to 128 KB. Private repositories, GitHub tokens, automatic updates and custom registries are not supported yet. A repository cannot silently replace an installed plugin with the same ID from another repository. Uninstall first to change its source.

Rebuilds use fixed ZIP timestamps, but toolchain changes can still change DEX output. Upload the manifest, ZIP and checksum from the same build. Keep the checksum outside the ZIP to avoid a circular checksum. Publish a new version when changing a release package.

## Package

A ZIP requires these files at its root:

```text
kiosk-satellite-plugin.json
plugin.jar
LICENSE
```

Optional files under `assets/` are included by the build tool for screensaver rendering. Asset contents count toward the existing 4 MiB package and expanded-size limits. See the [screensaver guide](screensavers.md) for supported paths and loading behavior.

`plugin.jar` contains `classes.dex` and optional additional `classesN.dex` files. It must not contain a copy of the SDK, Java class files or native libraries. The build tool compiles against the SDK and packages only your plugin classes. External dependencies need their own build integration and license review.

The complete ZIP, expanded files and expanded DEX content each have a 4 MB limit. The manifest has a 32 KB limit. SDK 1 permits up to eight installed plugins per kiosk.

## Manifest

See [kiosk-satellite-plugin.json](../kiosk-satellite-plugin.json) for a complete example.

| Field | Meaning |
| --- | --- |
| `schemaVersion` | Must be `1` |
| `apiVersion` | `1`, the first public SDK including all documented features |
| `id` | Stable lowercase ID with optional hyphens, at most 64 characters |
| `name` | Display name, at most 80 characters |
| `version` | Default local build version, formatted as `major.minor.patch` with optional prerelease suffix. Release builds override it from the tag |
| `minAndroidSdk` | Android API level, at least `24` |
| `minAppVersion` | The oldest Kiosk Satellite release the plugin works on, such as `2026.10.40`. Required to submit to Community Plugins. See [Minimum Kiosk Satellite version](#minimum-kiosk-satellite-version) |
| `entryClass` | Public class implementing `KioskPlugin` with a public no-argument constructor |
| `description` | Plain text, at most 1000 characters |
| `author` | Author name, at most 120 characters |
| `license` | License identifier, at most 120 characters |
| `capabilities` | Any required entries from `overlay`, `native`, `entities`, `host.read`, `host.control`, `ha`, `shizuku`, `screensaver`, `screensaver.mode`, `noise`, `voice.skin`, `voice.text`, `camera`, `barcode` and `ma`. `noise` and `voice.text` require `host.read`. `voice.skin` requires `overlay` and `host.read` |
| `settings` | Up to 50 settings. Kiosk Satellite versions without native overlays accept up to 20 |
| `commands` | Up to 20 named commands |
| `triggers` | Up to 20 named [gesture triggers](#gesture-triggers) |
| `icon` | Optional. A 256 by 256 PNG under `assets/`, at most 256 KB, that stands for the plugin in Community Plugins and Browse plugins. Without one they show the generic plugin icon |
| `voiceSkinThumbnail` | Optional. A PNG, JPEG or WebP under `assets/`, at most 256 KB, that shows the plugin's voice skin in the Skin picker. Requires `voice.skin`. See [Voice skins](voice-skins.md#thumbnail) |
| `headless` | Optional. `true` when the plugin also runs on a device without a screen, such as an Echo Dot. See [Devices without a screen](#devices-without-a-screen) |

A setting declares `key`, `title`, `type` and `default`. Types are `string`, `secret`, `boolean`, `number`, `color`, `select`, `entity`, `icon`, `dashboard`, `cameraView`, `list` and `action`. Strings and secrets allow up to 512 characters. See [Pickers, lists and conditions](#pickers-lists-and-conditions) for `icon`, `dashboard`, `cameraView` and `list`, and [Action settings](#action-settings) for `action`. Keys start with a letter and contain letters, digits or underscores. A command or trigger declares `id` and `title`. Their IDs start with a lowercase letter and contain letters or digits. IDs must be unique within their respective lists.

Settings and commands are scoped to the plugin ID. Do not change that ID after publication. Increasing a plugin's own version does not increase the SDK version.

Declared commands are reusable actions. Users can select them in **Gestures > Run a plugin action**. The plugin subpage lets users opt each command into the kiosk drawer or expose it as an ESPHome button in Home Assistant. Settings configures those placements and does not run the command. These connections work with all supported SDK versions without additional plugin capabilities.

Keep command IDs stable across releases. The host retains shortcut choices for commands that survive an update and removes choices for deleted commands. New commands start without drawer or Home Assistant exposure. Commands run through `execute` only while the plugin is running. A saved gesture targeting a disabled or missing plugin reports a failure. Plugins should use hardware entities such as RGB lights for ongoing stateful control and commands for individual operations.

### Minimum Kiosk Satellite version

Set `minAppVersion` to the oldest Kiosk Satellite release that has every host method and manifest field the plugin uses. Raise it when a release of the plugin starts using something newer.

Kiosk Satellite releases after 2026.10.39 check it at preview, install, update and fleet sync. If the kiosk runs an older release than `minAppVersion`, Kiosk Satellite refuses the plugin with a message that names both versions. The plugin already installed keeps running, and **Browse plugins** says which release it needs instead of offering the update.

Kiosk Satellite 2026.10.39 and older ignore `minAppVersion`. If the plugin still supports them, keep the `LinkageError` guards from [Older versions](setup-controls.md#older-versions).

## Gesture triggers

Declared triggers let a plugin start any gesture action, the way a corner tap or a clap does. Users pick them in **Gestures > Plugin trigger** and choose what each one runs: a dashboard view, a Home Assistant script or anything else the Gestures page offers. Fire a trigger with `host.fireTrigger(id)` when the plugin notices something KS does not watch by itself, such as a button press reported through [`device.key`](ks-api.md#hardware-keys) or a proprietary sensor.

```json
"triggers": [
  { "id": "hardwareKey", "title": "Hardware key" }
]
```

```java
host.fireTrigger("hardwareKey");
```

- Each fire runs every gesture mapped to that trigger. With nothing mapped, it does nothing.
- KS drops fires past four per second per plugin instead of failing the plugin, so a held or bouncing button cannot flood actions.
- An undeclared ID throws `IllegalArgumentException`. Calling it after the plugin stops throws `IllegalStateException`.
- Lockdown Mode and Kiosk Mode's Disable Gestures silence triggers like the other gestures.
- Kiosk Satellite versions without gesture triggers throw `UnsupportedOperationException`. Catch it if the plugin should keep working there.
- No extra capability or SDK version is required.

Keep trigger IDs stable across releases. A gesture saved for a trigger that a later version removes no longer fires. Plugins are installed per kiosk, so Fleet Management does not sync gestures that use a plugin trigger. Hello World fires `hardwareKey` from its Hardware keys demo.

## Action settings

An `action` setting lets the user pick any Kiosk Satellite action in the plugin's settings, with the same chooser the Gestures page uses: a dashboard view, a Home Assistant script or service, another app, another plugin's action and the rest. Run the saved action with `host.runAction`. A plugin bar or menu can then open whatever the user chose, with no code for any particular action and no trip to the Gestures page.

```json
{ "key": "tap", "type": "action", "title": "Tap action", "default": {} }
```

```java
host.runAction("tap", (ok, data, error) -> {
    if (!ok) host.log("Tap action: " + error);
});
```

In a list, name the row field by the list key, the row index and the field key, such as `items.2.action`:

```json
{
  "key": "items", "type": "list", "title": "Items", "default": [],
  "row": { "title": "label", "subtitle": "action", "icon": "icon" },
  "fields": [
    { "key": "label", "type": "string", "title": "Label", "required": true },
    { "key": "icon", "type": "icon", "title": "Icon" },
    { "key": "action", "type": "action", "title": "Action", "required": true }
  ]
}
```

- Only the user chooses an action. `saveSettings` keeps action settings, and lists that hold an action field, as the user saved them. `updateSettings` throws `IllegalArgumentException` for them. The default must be `{}`, so a manifest cannot choose one either.
- The plugin decides when an action runs, never what it does. That is why `runAction` needs no capability: a plugin without `ha` can still run the Home Assistant script the user picked.
- `configure` receives the action as a read-only `Map`, or an empty one when none is chosen. Treat it as opaque and check only whether it is empty. Its fields can change between Kiosk Satellite releases.
- Show your own label for the action, such as a list row's `label` field. As a row's `subtitle`, an action field shows what the action does. It cannot be the row's `title`.
- The callback hears `ok=false` with an error when no action is chosen, the row does not exist, Lockdown Mode is on or the action fails. Kiosk Mode's Disable Gestures does not stop it, since the plugin runs the action from a control of its own.
- KS refuses runs past four per second per plugin with `Too many actions`.
- An action setting cannot be the target of `visibleWhen`.
- A path that names no action setting throws `IllegalArgumentException`. Kiosk Satellite versions without action settings throw `UnsupportedOperationException` and reject a manifest that uses the type, so set `minAppVersion` to 2026.10.42 or later.

Hello World's **Action demo** group has a Run button for a single action and a list of quick actions that show in its top bar's More menu. See [ActionDemo.java](../src/me/jxl/kiosk/plugins/hello/ActionDemo.java).

## Lifecycle

Implement `me.jxl.kiosk.plugins.KioskPlugin`:

| Callback | Responsibility |
| --- | --- |
| `start(host, settings)` | Save the host handle and acquire resources |
| `configure(settings)` | Apply the complete validated configuration |
| `execute(command, arguments)` | Run a declared command. SDK 1 passes an empty arguments map |
| `onEvent(event, payload)` | Handle a window or overlay event, RGB command or subscribed KS event. See the [complete interaction reference](ks-api.md) |
| `stop()` | Release timers, threads and resources |

Callbacks run serially on a worker dedicated to the plugin. They must finish within three seconds. The host disables a plugin after a callback error or timeout. Keep long work asynchronous and honor interruption. Host access is revoked after the plugin stops. SDK 1 read and subscription calls made after revocation throw. A timed-out thread can keep running if it ignores interruption, since this runtime does not isolate plugin code.

The host rejects settings with unknown keys or incorrect types. Defaults fill missing keys. First-time installation does not run plugin code and leaves the plugin disabled. Updates automatically stop the old session and restart the replacement if the plugin was enabled and the master switch is on. Disabled plugins stay disabled. Updates while the master switch is off retain the enabled choice without running code. The host calls `start` after explicit enable or at app startup for an enabled plugin while the master **Enable Plugins** switch is on. Turning the master switch off calls `stop` and revokes host callbacks without changing the plugin's saved enabled choice or settings. Turning it on starts the selected plugins again. An off master switch prevents startup and execution across app restarts.

To replace a loaded plugin, preview the same repository again and install its replacement release. KS stops the old session and restores its enabled state automatically. A normal update does not require an app restart. Compatible settings are retained. Settings and list row fields that the new version no longer declares are dropped. Removing a plugin deletes its saved settings.

## Floating window

The host exposes these methods:

```java
host.showWindow("Hello World", "Your message", "Say hello");
host.hideWindow();
host.log("A short diagnostic message");
```

`showWindow` creates or updates the plugin's one window. Updating its text keeps its drag position. Titles and button labels allow up to 80 characters. Messages allow up to 4096 characters. An empty button label hides the action button. All text is rendered as plain text by Flutter.

The action button sends `window.action`. Closing the window sends `window.closed`. Closing does not disable the plugin. Calling `hideWindow` does not send a close event, so plugins can manage visibility without a callback loop.

The window floats over the dashboard and is draggable by its title bar. Other kiosk surfaces, including the drawer and screensaver, can cover it. It does not create an Android system overlay. No dashboard DOM access, custom HTML or arbitrary Flutter widget loading is provided.

## Native overlays

For anything beyond a text window, draw your own Android views with `showOverlay`. A bar at the top of the kiosk and a full screen panel both work, styled with the KS colors, shapes and typeface. See [native overlays](overlays.md).

## Devices without a screen

Kiosk Satellite runs headless on a device without a screen, such as an Echo Dot. Overlays, the floating window and screensavers never show there. A plugin that declares `overlay` or `screensaver` does not install on such a device, from the Plugin Manager or a fleet leader, and reports **This plugin needs a screen**.

A plugin with more to offer than its display, such as entities or Home Assistant automation, sets `"headless": true` in its manifest. It then installs everywhere and checks `host.isHeadless()` in `start` to skip its overlays, window and screensavers. A plugin that declares neither capability runs headless without the flag.

```java
@Override public void start(PluginHost host, Map<String, Object> settings) {
    this.host = host;
    publishSensors();
    if (!host.isHeadless()) showBar();
}
```

## Trust and compatibility

A plugin executes inside Kiosk Satellite with the application's identity. This is not a sandbox. The capabilities array identifies SDK requirements and does not restrict arbitrary Java code. Do not copy app implementation classes into your plugin or rely on internal classes discovered through reflection.

SDK 1 includes native libraries, RGB entities, explicit state queries, passive events and transient controls. See the [complete interaction reference](ks-api.md) for the exact contract and exclusions. Repository updates are reviewed from the installed entry row.

Publish source code alongside release packages. A checksum detects changed bytes but does not authenticate a publisher. Users must trust the repository author. Plugin authors remain responsible for all licenses and notices included in their packages.

## Rich settings and hardware

These features are part of SDK 1. Declare `apiVersion: 1` and the capabilities your plugin needs.

Settings can add `group` and `description` fields. A `number` setting declares finite `min`, `max`, `step` and a numeric default. Values must match that range and step. It shows as a slider, which allows at most 10000 steps from `min` to `max`. Add `"input": "field"` to show a number field instead, for a wide range or an exact value such as a temperature limit. The field has no limit on the number of steps, opens the number keyboard on the kiosk and shows the `unit` after the value. It saves only a number inside the range and on a step, and shows why it does not take anything else. `configure` still receives a number. `input` also works on a number field in a list row, and `"slider"` is the default. Older Kiosk Satellite versions ignore `input` and show a slider, so they reject a manifest whose range has more than 10000 steps or ends off a step. A `color` setting stores `#RRGGBB` and uses the app color picker. A `select` setting declares up to 32 unique string `options` and a default from that list. A `secret` setting holds a password, token or API key. Its default must be an empty string. The plugin receives the value as a string in `configure`, the same as a `string` setting. The Plugin Manager and Remote Admin mask it and only let users replace or clear it. `listPlugins` and `getPluginState` report a saved secret as `__set__` and an empty one as an empty string, never the value. Older Kiosk Satellite versions reject a manifest that declares a `secret` setting. The same setting types appear in the native app and Remote Admin. On the kiosk, string settings show their current value and open a text edit dialog when tapped. Each completed edit saves automatically. Sliders save on release. Remote text fields save on blur or Enter. There is no separate Save settings row. Each save sends only the changed setting, and `configure` receives the complete settings object, so plugins should avoid expensive work when unchanged values are submitted. For buttons on the settings page and choices the plugin finds at runtime, see [settings buttons and plugin choices](setup-controls.md).

The `native` capability allows shared libraries under `native/ABI/libNAME.so`, with arm64-v8a, armeabi-v7a and x86_64 supported. ELF architecture and stored digests are checked. The existing 4 MB package and expanded-content limits still apply. The host supplies a separate native library path for each session, so a disabled plugin can be enabled again without reusing a library owned by a previous class loader. Files potentially referenced by loaded code remain until a later app start.

| Host method | Purpose |
| --- | --- |
| `nativeLibraryPath(name)` | Absolute path to a verified session copy of `libNAME.so`. Requires `native` |
| `packagePath()` | Verified DEX container path for a plugin-owned helper. Requires `native` |
| `assetPath(name)` / `openAsset(name)` | Path to, or a stream of, a verified file under `assets/`, such as a font or icon set. No capability is required. Older Kiosk Satellite versions throw `UnsupportedOperationException` |
| `status(message, error)` | Runtime status text in the plugin subpage, up to 1000 characters |
| `saveSettings(values)` | Validate and persist plugin-originated setting changes |
| `updateSettings(values)` | Save only the named settings. The rest keep their saved values, secrets included. See [settings buttons and plugin choices](setup-controls.md#settings-that-depend-on-others) |
| `publishLight(key, name, effects, state)` | Register or update an RGB light. Requires `entities` |
| `removeLight(key)` | Remove a light from this plugin's active catalog |

RGB state contains `on`, `brightness`, `red`, `green`, `blue` and `effect`. Numeric channels use 0 to 1. A plugin can publish up to four lights and each can advertise up to 24 effects. Home Assistant commands arrive through `onEvent("light.KEY", payload)` with the fields that were supplied. Stable object IDs are namespaced by plugin ID and key. Command handlers should update their internal state, apply the change and publish the resulting state. Use `saveSettings` when the command changes persisted settings.

Entity catalog changes reconnect ESPHome. State updates do not. Disabling a plugin revokes its host and removes its entities. Plugin-owned threads and helper processes must stop before `stop()` returns. The three-second callback deadline still applies. Long permission prompts must happen on a plugin-owned worker that can be canceled during shutdown.

The Rockchip LED Control repository demonstrates these additions without putting a device driver into KS itself.

## Sensors, selects and switches

The `entities` capability also supports numeric sensors, text sensors, binary sensors, writable selects and switches. See the [entity API](entities.md) for signatures, metadata, lifecycle rules and migration steps. Hello World demonstrates all five with simulated readings, a select for its Pattern setting and a switch for Show demo chart.

## Charts

Publish line or bar charts with `host.publishSeries(key, chart)` and remove it with `host.removeSeries(key)`. Charts are read-only runtime displays, separate from settings. They render in the plugin subpage on-device and in Remote Admin. No extra capability or SDK version is required. The [chart reference](charts.md) covers the schema, limits, lifecycle and steps for updating an existing repository. Hello World demonstrates every settings control alongside a live simulated chart.

## Status tiles

Put a verdict on the Remote Admin Overview Status panel with `host.publishStatusTile(key, title, level, text)` and take it down with `host.removeStatusTile(key)`. Levels are `on`, `warn`, `off` and `""`, the same vocabulary the built-in tiles use. Plugin tiles follow the built-in six, name their plugin and open the plugin's subpage. No extra capability or SDK version is required. The [status tile reference](status-tiles.md) covers the limits and lifecycle. Hello World publishes one tile driven by its Status tile demo settings.

## KS state and transient controls

Declare `host.read` to inspect supported KS state and subscribe to passive events. Declare `host.control` for transient controls such as dismissing the screensaver or showing and hiding camera views and Now Playing. The [complete KS interaction reference](ks-api.md) lists every command, event, payload, capability and limit and includes a buildable example.

## Camera and barcodes

Declare `barcode` to open the KS scanner and get back the code it reads, or `camera` to receive frames from the device camera. Both share the camera with motion detection and the other camera features. The [camera and barcode guide](camera.md) covers the options, results, limits and the Hello World demos.

## Shizuku

Declare `shizuku` for optional privileged command execution through KS. The [Shizuku guide](shizuku.md) documents setup, user permission, state events, command results and lifecycle limits. Use the separate example to test access without changing the standard Hello World template.

## Screensavers

Declare `screensaver`, bundle an HTML entry and its resources under `assets/` and register it with `host.publishScreensaverAsset(key, title, entry, data)`. Files load on demand from the verified package. Small inline renderers can still use `host.publishScreensaver(key, title, html)`, and `host.publishScreensaverView(key, title, factory)` draws one with a native Android view. It becomes a selectable stock screensaver mode. KS owns all screensaver policy and removes the renderer when the plugin session ends. Declare `screensaver.mode` to choose which screensaver shows. Hello World includes a bouncing DVD logo with color and size settings. The [screensaver reference](screensavers.md) covers the contract, document limits, lifecycle and fleet behavior.

## Pickers, lists and conditions

Three setting types open the app's own pickers, so users never type an ID:

| Type | Stores | Picker |
| --- | --- | --- |
| `icon` | An MDI name such as `mdi:garage`, or an empty string | Every Material Design Icon, searchable by name |
| `dashboard` | A Home Assistant path such as `lovelace/0` or `energy`, or an empty string | The dashboard and view picker, which also lists Home Assistant's own pages such as Energy. Pass the value to `haNavigate` |
| `cameraView` | A KS camera view ID, or an empty string | The kiosk's camera views by name. Pass the value to `showCameraView` |

A `list` setting holds rows that users add, edit, reorder and delete. Each row is a small form described by `fields`. Row fields take the same types as settings, except `secret`, and can be `required`. On the kiosk the rows show inline on the plugin page and each opens an editor. Remote Admin works the same way and also moves rows with arrow buttons. The whole list saves when the editor's Save is pressed, a row is deleted or rows are reordered.

```json
{
  "key": "items", "type": "list", "title": "Items", "group": "Items",
  "addTitle": "Add item", "maxItems": 12,
  "row": { "title": "label", "subtitle": ["page", "opens"], "icon": "icon" },
  "default": [{ "label": "Home", "opens": "Page", "page": "lovelace/0" }],
  "fields": [
    { "key": "label", "type": "string", "title": "Label", "required": true },
    { "key": "icon", "type": "icon", "title": "Icon" },
    { "key": "opens", "type": "select", "title": "Opens", "options": ["Page", "Menu"] },
    { "key": "page", "type": "dashboard", "title": "Page", "required": true,
      "visibleWhen": { "key": "opens", "equals": "Page" } },
    { "key": "entries", "type": "list", "title": "Entries", "minItems": 1,
      "visibleWhen": { "key": "opens", "equals": "Menu" },
      "fields": [{ "key": "label", "type": "string", "title": "Label", "required": true }] }
  ]
}
```

| List field | Meaning |
| --- | --- |
| `fields` | 1 to 16 row fields. A row field may leave out `default`. Its type then supplies one: an empty string, `false`, a number's `min`, a select's first option, `#FFFFFF` or an empty list |
| `maxItems` | Up to 32 rows, which is also the default |
| `minItems` | Rows the list needs while it is shown. 0 unless set |
| `addTitle` | The add row's label and the new row editor's title, such as `Add item`. Defaults to Add |
| `row` | What a row shows: `title` is a field key and defaults to the first `string` field. `subtitle` is a key or up to four keys, and the first one that is shown and not empty wins. `icon` names an `icon` field. An `entity` field shows the entity's friendly name and state. As the title it also brings the entity's icon unless the row has its own, with its state and entity ID below unless `subtitle` has something to show |

A list can hold one nested list, which opens inside the same editor. Lists cannot go deeper. The editor's Save stays off until every shown `required` field is filled in and every shown list has `minItems` rows. A list counts as one of the 50 settings, and its saved value is at most 32 KB.

`configure` receives a list as a read-only `List` of `Map` rows, in the order the user set. KS fills in every field, so each row has all its keys:

```java
@SuppressWarnings("unchecked")
List<Map<String, Object>> items = (List<Map<String, Object>>) settings.get("items");
for (Map<String, Object> item : items) {
    String label = (String) item.get("label");
}
```

Any setting or row field can declare `visibleWhen`, so it shows only while another field holds a value. The condition names a field declared earlier on the same level and has one test: `equals` or `notEquals` with a value or an array of values, or `"notEmpty": true`. A field that depends on a hidden field hides too. Hidden fields keep their saved values, so read only the fields that apply. `required` counts only while the field is shown.

Older Kiosk Satellite versions reject a manifest that uses these types, `visibleWhen` or `required`. Hello World's **List demo** group uses each of them, and [ListDemo.java](../src/me/jxl/kiosk/plugins/hello/ListDemo.java) reads the list in `configure`.

## Home Assistant

Use the `entity` setting type for KS's searchable Home Assistant entity picker in both interfaces. The value is an entity ID or an empty string. Declare `host.read` to read its state or subscribe to changes. Declare `ha` to call services, run other WebSocket API commands and subscribe to triggers, events and templates with `host.haCall` and `host.haSubscribe`. See the [Home Assistant guide](home-assistant.md) and the Hello World demo.

## Music Assistant

Declare `host.read` to read the kiosk's own Sendspin player with `getSendspinPlayer` and the ID of the Music Assistant player that stands for it. Declare `ma` to run Music Assistant commands for players, queues and the library with `host.maCall` and follow its events with `host.maSubscribe`, through the kiosk's own connection and token. See the [Music Assistant guide](music-assistant.md) and the Hello World demo.

## Group settings, charts and readings

An optional top-level `groups` array orders settings groups and places their charts and readings directly below their settings. Use the same title as the settings' `group` field:

```json
"groups": [
  {
    "title": "Chart demo",
    "charts": ["demo"],
    "readingsTitle": "Chart readings",
    "readings": ["sensor.wave", "text_sensor.summary"]
  },
  {
    "title": "Home Assistant demo",
    "readingsTitle": "Home Assistant readings",
    "readings": ["text_sensor.ha_state", "text_sensor.ha_details"]
  }
]
```

Charts appear after the group's settings, followed by a separate readings section. `readingsTitle` defaults to `Readings`. Chart references use the key passed to `publishSeries`. Reading references use `type.key`, where type is `sensor`, `text_sensor`, `binary_sensor`, `select` or `switch`. Each reference can belong to only one group. A plugin that makes its keys at runtime claims them with a wildcard instead: `"*"` takes every reading or chart no other group names, and `"sensor.*"` takes every reading of one type. A group naming a key wins over a type wildcard, which wins over `"*"`. Older Kiosk Satellite versions reject a manifest with wildcards. Hello World's **Device stats demo** group claims the chart of each tile with `"charts": ["*"]`.

```json
{ "title": "Home Assistant", "readingsTitle": "Sent to Home Assistant", "readings": ["*"] }
``` References may name readings or charts that have not been published yet. Empty output sections stay hidden.

Declare at most 20 groups, with unique titles matching existing settings groups. A group can reference up to four charts and 32 readings. Settings groups omitted from `groups` follow in their original manifest order. Unassigned charts and readings appear after the settings groups. These placements affect only the plugin subpage, not Home Assistant entities or their names. Runtime updates preserve settings edits and chart interaction state. All fields remain part of SDK 1.
