# Changelog

## Unreleased

### Fixed

- Keep Community Plugins download counts steady. GitHub sometimes answers a release list with an empty page, which showed a plugin with 0 downloads. The refresh now asks again and never lowers a count from one refresh to the next.

- Updating a plugin that removes a field from a list setting's rows no longer fails with "Unknown plugin setting" when rows were saved. Kiosk Satellite drops the field from the saved rows, the way it drops a removed setting. Document it under Settings.

### Added

- Add `includeHidden` to `entity` settings and `entity` fields in `list` settings. With it, the entity picker also lists the entities Home Assistant hides, marked with the hidden icon. Older Kiosk Satellite releases ignore it. Document it under Choose an entity in Settings in Home Assistant.

- Document the barcode scanner's screen light button, which turns the screen white around a smaller preview at full brightness to light the code for a front camera, and which the kiosk remembers for the next scan. Document it under Scan a code in Camera.

- Add `minAppVersion` to the manifest, the oldest Kiosk Satellite release a plugin works on. Kiosk Satellite releases after 2026.10.39 refuse a plugin that needs a newer release at preview, install, update and fleet sync, with a message that names both versions, and Browse plugins shows the release it needs instead of Install or Update. Community Plugins now requires it on new submissions and checks that it names a published Kiosk Satellite release. Listed plugins keep passing without it. Hello World sets it to 2026.10.36. Document it under Minimum Kiosk Satellite version in Creating plugins.

- Document the Assistant 1 skin and Assistant 2 skin settings that replace Skin, so a voice skin can be picked for one assistant alone. `voice.skin` and `skinSelected` now follow the assistant answering the turn on screen, and while nothing shows they are true if either setting picks the plugin. Document it under When to draw in Voice skins and in the KS API reference.

- Add `launchApp` for `host.control` and `getLauncherApps` for `host.read`. A plugin can open only the apps added to the App Launcher. Kiosk Mode's Allowed Apps and Lockdown Mode apply. Document them under Open an app in the KS API reference.

- Show the newest SDK features in Hello World. The new **Device stats demo** group draws a native **Device stats** screensaver with `publishScreensaverView`, with a card for each of the kiosk's `getStats` readings that its **Tiles** list picks, `cpuClock` and `batteryTemp` among them. **Reading** in each row takes grouped plugin choices with their current values, which the plugin refreshes only while `settings.page` says the page is open. Each tile gets a chart, which the group claims with `"charts": ["*"]`. **Temperature warning** is a number field with `"input": "field"`. The **Use device stats screensaver** and **Restore screensaver mode** actions set and clear a `screensaver.mode` override, and the **Screensaver mode** reading shows `getScreensaverMode`. In the **Setup demo** group, **Device** shows an `emptyText` and `setSettingChoicesEnabled` turns it off while the address contains `fail`. Hello World now needs Kiosk Satellite 2026.10.36 or newer, which the README now says.

- Add `cpuClock` to `getStats`, the average clock of the active CPU cores in MHz. `cpu` is now null on a device that does not let an app measure CPU load, where it used to report the clock position. Document both under Read commands in the KS API reference.

- Add `batteryTemp` to `getStats`, the battery temperature in Celsius. It is null on a device without a battery or one whose battery has no temperature sensor. Document it under Read commands in the KS API reference.

- Add `host.setSettingChoicesEnabled(key, enabled)`, which turns a choices setting or list row field off while it cannot work and back on. Off, its picker and Refresh do not open. Document it under Plugin choices.

- Add `emptyText` for settings and list row fields with plugin choices. Their field and picker show it until the plugin lists any choices, in place of "No choices yet. Refresh to look for them." Document it under Plugin choices.

- Add the `settings.page` event. Kiosk Satellite sends `{"open": true}` when the plugin's settings page opens on the kiosk or in any Remote Admin browser and `{"open": false}` once it is closed everywhere, so a plugin can refresh its choice values only while someone looks. Document it under Know when the settings page is open in the Settings buttons and plugin choices guide.

- Add `"input": "field"` for `number` settings and list row fields. It shows a number field instead of a slider, for a wide range or an exact value, and lifts the 10000 step limit. Document it under Rich settings and hardware.

- Let a display group claim readings and charts with wildcards. `"*"` takes every one no other group names and `"sensor.*"` every reading of one type, for plugins that make their keys at runtime. Document them under Group settings, charts and readings.

- Add native screensavers. `host.publishScreensaverView(key, title, factory)` registers a screensaver drawn by an Android view, with the KS theme, fonts, icons and UI scale. Document it under Draw it natively in the screensaver guide.

- Add the `screensaver.mode` capability with the `getScreensaverMode`, `setScreensaverMode` and `clearScreensaverMode` commands, which let a plugin choose which screensaver shows while it runs. Document them under Switch screensavers in the screensaver guide. Community Plugins accepts the capability.

- Add plugin choices in list rows and grouped choices. A `string` field inside a list or nested list takes `"choices": "plugin"` under its dotted path, such as `panels.tiles.sensor`. A new `setSettingChoices(key, choices)` overload takes up to 2000 maps with a label, group, detail, value, category and icon, and the picker shows them the way the entity pickers do. Kiosk Satellite keeps the latest list across restarts. `getPluginSetup` takes and returns `choiceRevisions`. Document them in the Settings buttons and plugin choices guide.

- Add three submission rules to Submit your plugin. The plugin name can't be just a trademark or another product's name. The icon must be the author's own artwork or the unmodified logo of the service the plugin connects to, with a note in the README that the plugin isn't affiliated with that company. The README must include the Installation section with the Install in Kiosk Satellite button.

- Add the `openCalendar` and `hideCalendar` controls to `host.control`, so a plugin can open the Kiosk Satellite calendar on a view and day and close it again (kiosk-satellite#1062).

- Add the `ma` capability with `host.maCall(command, args, callback)`, which runs one Music Assistant API command such as `player_queues/play_media`, and `host.maSubscribe(key, filter, callback)` and `host.maUnsubscribe(key)`, which follow the Music Assistant events a filter names through `ks.ma.subscription.KEY` events. KS signs in with its own Music Assistant token and refuses sign-in, user, token and server configuration commands. Add the `getSendspinPlayer` read and the `sendspin.player` event, which give the kiosk's own Sendspin player and the Music Assistant player and server that stand for it. Hello World shows the kiosk's Music Assistant player and its volume under **Music Assistant player** and **Music Assistant volume** and adds a **Play or pause Music Assistant** action. Document all of them in the new Music Assistant guide. Community Plugins accepts the capability. Hello World now needs a Kiosk Satellite version with it.

- Check submit and remove requests opened through the GitHub API or the gh CLI. GitHub applies the issue form's label only on the website, so the check now also recognizes a request by its "Submit plugin:" or "Remove plugin:" title and adds the missing label.

- Run the Community Plugins check when a maintainer adds the submission or removal label to an issue by hand, as well as when the issue is opened or edited.

- Add the `barcode` and `camera` capabilities. `scanBarcode` opens the Kiosk Satellite scanner and answers with the code it reads and its format, and `cancelBarcodeScan` closes it. `openCamera` delivers frames from the device camera as the new `CameraFrame`, in luminance or NV21, until `closeCamera` or the time limit. Hello World gets a **Scan a code** action that publishes the code as readings and fires a `hello_world_barcode` event in Home Assistant, and a **Show camera preview** action that draws the frames in color in a small overlay with a brightness reading. Document both in the new Camera and barcodes guide. Community Plugins accepts both capabilities. Hello World now needs a Kiosk Satellite version with them.

- Hello World's author is now jxlarrea.

- Add the Install in Kiosk Satellite badge and an Installation section plugin authors copy into their README, with a one-click install button and manual steps, the way HACS repositories show theirs.

- Give Hello World an icon, the Kiosk Satellite mark, through the new `icon` manifest field. Community Plugins shows it.

- Add Community Plugins, the plugin directory on kiosksatellite.com and under **Plugin Manager > Browse plugins**. Authors submit a plugin through the new **Submit a plugin** issue form. A workflow checks it the way Kiosk Satellite checks an install and lists it in `community/plugins.json` once a maintainer approves it. `tools/community_plugins.py check` runs the same check from a clone. Add the optional `icon` manifest field, a 256 by 256 PNG under `assets/` that the directory shows. The README now covers submitting a plugin, and the guide it held moved to the new Getting started page.

- Document that a list row shows an `entity` field the way the entity picker does: its friendly name and state, and as the row title its icon and entity ID too.

- Document that overlays follow the kiosk's **Scale UI** setting. The context passed to `OverlayFactory.create` has a density that includes it, so `KsTheme.px`, dp and sp grow with it, and KS scales `OverlaySpec` sizes, insets and offsets and `setContentInsets` to match. A new scale builds the view again.

- Add settings buttons and plugin choices. A top-level `buttons` array puts buttons on the plugin's settings page, each closing its settings group, with an optional `confirm` dialog and `visibleWhen`. A press arrives as `settings.button` with a run the plugin reports on through `host.setupResult(run, state, message)`. `settings.cancel` ends a canceled, replaced or timed out run. `host.confirmSetup` asks the person a question partway through a run, answered through `settings.confirm`. A `string` setting with `"choices": "plugin"` lists what `host.setSettingChoices` publishes. Refresh arrives as `settings.choices`. Add `host.updateSettings(values)`, which saves only the named settings. The new **Setup demo** group tests a pretend address, pairs with a fingerprint question and finds devices that fill in a dependent setting. Document all of them in the new Settings buttons and plugin choices guide.

- Add voice skins. A plugin that declares the new `voice.skin` capability appears in **Voice Satellite > Appearance > Skin**, and while it is picked KS draws none of its own overlay for a turn. Add the `voice.overlay`, `voice.level` and `voice.skin` events and the `skinSelected` and `overlay` fields of `getVoiceState`, which tell a skin when to draw and how loud the voice is. Add the `voice.text` capability with `getVoiceText` and the `voice.text` event, which give the command, the answer and the tool lines as the Conversation settings allow. Add `getVoiceAppearance` and the `voice.appearance` event, which give a skin the Theme, Background, Text size and Reactive activity bar settings, and keep `voice.level` at 0 while Reactive activity bar is off. Add the optional `voiceSkinThumbnail` manifest field, which names the skin's picture in the Skin picker. Hello World is now a voice skin with a full screen look and a docked bubble, both with a ring that follows the voice and the conversation and both following the Appearance settings, and a thumbnail. Document all of them in the new Voice skins guide. Hello World now needs a Kiosk Satellite version with voice skins.

- Add the optional `headless` manifest field and `host.isHeadless()` for devices without a screen, such as an Echo Dot. A plugin that declares `overlay` or `screensaver` installs there only with `"headless": true`, and then checks `isHeadless()` to skip its display features. Document both under Devices without a screen.

- Document the SDK 1 `setWakeWordMuted` control, which mutes or unmutes wake word detection like Voice Satellite's Mute switch, and the `muted` field `getWakeWordState` now returns.

- Add the `ha` capability with `host.haCall(message, callback)`, which sends one Home Assistant WebSocket API command such as `call_service`, and `host.haSubscribe(key, message, callback)` and `host.haUnsubscribe(key)`, which follow triggers, events and templates through `ks.ha.subscription.KEY` events. KS signs in with its own token, numbers the messages and refuses credential commands. The Hello World Home Assistant demo now follows its entity with a state trigger under **Home Assistant trigger** and adds a **Toggle Home Assistant entity** action. Document both in the Home Assistant guide, which now covers reads, calls and subscriptions.

- Add `host.setContentInsets(left, top, right, bottom)`, which reserves space along the screen edges so Home Assistant pads its views, dialogs and toasts clear of a plugin's bar. The Hello World top bar reserves its depth while it shows. Document it, including the `--ks-content-inset-*` variables custom pages read.

- Add per-edge insets with `OverlaySpec.inset(left, top, right, bottom)` and `offset(x, y)`, which KS keeps on screen, `closeOnOutsideTouch` and `passOutsideTouch` for popups, and `enter`, `exit` and `transitionDuration` with fade, slide and scale transitions. Add `KsTheme.icon(name, role)` and `KsTheme.iconPath(name)`, which draw KS's own Material Design Icons. The top bar now slides in and out, and its new **More** button opens a menu under it that closes on an outside tap. Document all of them and the `LinkageError` fallback for older Kiosk Satellite versions.

- Document the SDK 1 `getCameraViews` and `getDashboards` reads, which list camera views by name and Home Assistant dashboards with their views, ready for `showCameraView` and `haNavigate`.

- Add `host.assetPath(name)` and `host.openAsset(name)`, which give plugin code a verified file from its own `assets/` folder, such as a font.

- Document that `haNavigate` accepts a query string and a fragment, and that it returns true once the page is on the view.

- Document the overlay threading contract: `showOverlay` and `hideOverlay` never block, and `create` and `onDestroy` run later on the main thread. Document that creating an overlay is the expensive part, so plugins should reuse a long-lived overlay, and that screenshots can miss native overlays.

- Find `d8.bat` on Windows in `tools/build.py`, and skip the two asset tests that need symbolic links or `?` and `#` in file names there.

- Add a List demo group with a Shortcuts list setting, whose rows open a page, a camera view or a nested list of menu entries, and Favorite page, Featured icon and Camera view picker settings. Camera view only shows while Use a camera view is on. Shortcuts received and Picker values under List demo readings show what `configure` received. Document the `list` setting type, which holds rows users add, edit, reorder and delete, with one nested list allowed. Document the `icon`, `dashboard` and `cameraView` picker types and the `visibleWhen` and `required` fields. Hello World now needs a Kiosk Satellite version with these setting types.

- Document the `secret` setting type for passwords, tokens and API keys. Kiosk Satellite masks it in the Plugin Manager and Remote Admin and reports a saved value as `__set__` in `listPlugins` and `getPluginState`.

- Add `KsTheme.styleSlider`, which styles a `SeekBar` like the Kiosk Satellite sliders, and a slider to the Hello World top bar.

- Add SDK 1 native overlays: with the `overlay` capability, `host.showOverlay(key, spec, factory)` shows a plugin's own Android view over the kiosk, from a small anchored bar to a full screen panel, and `hideOverlay(key)` removes it. `OverlaySpec` sets the anchor, size, inset, whether back closes it, whether it draws on top in the voice overlay's slot over the screensaver and camera views and whether touches pass through. `KsTheme` passes the KS colors, radii, spacing and Rubik typeface, with card and pill button helpers, and restyles live overlays when the theme changes. Back sends `overlay.closed`. Add Show top bar, Hide top bar, Show full screen overlay, Show edge glow and Hide edge glow actions that demonstrate a bar, a full screen card and a visual-only animated glow on top, and an overlay guide.

- Document the new limit of 50 settings per plugin, up from 20.

- Compile the SDK against the Android platform in `tools/build.py` and `tools/test.py`, since the overlay classes use Android views.

- Add the SDK 1 `getIntercomState` read and `intercom.state` event, which follow this kiosk's intercom with the same state, other kiosk and Do not disturb values as its ESPHome entities. Add an Intercom demo group that shows them under Readings while Show intercom state is on. Document the states and how long ended and missed hold.

- Add the SDK 1 `getVoiceState` read and `voice.state` event, which follow this kiosk's Voice Satellite turns through idle, listening, processing and responding, realtime conversations included. Add a Voice Satellite demo group that shows the state under Readings while Show Voice Satellite state is on. Document the states, the read and event pairing and the fallback for older Kiosk Satellite versions.

- Add SDK 1 gesture triggers: declare `triggers` in the manifest and call `host.fireTrigger(id)` so a gesture mapped to the trigger runs its action. The Hardware keys demo declares a Hardware key trigger and fires it for each press. Document the manifest field, the four fires per second limit, the errors and the fallback for older Kiosk Satellite versions.

- Document the SDK 1 `setVolume` control and the `channel` argument of `getVolume`, covering the master, media, assistant and intercom volumes, and the `device.volume` event now firing for all four.

- Add a Hardware keys demo group that subscribes to `device.key` while Show hardware keys is on and shows the last key, its scan code and a press count under Readings. Document the SDK 1 `device.key` event, its payload, filtering of typed text and its queue limit.

- Add a Status tile demo group that publishes a Hello World demo tile on the Remote Admin Overview Status panel, with a toggle, a level selection and a text setting. Document the SDK 1 status tile API, its limits, lifecycle and the `getPluginStatusTiles` remote command.

- Keep each demo together using manifest display groups. Chart demo settings are followed by the chart and Chart readings. Home Assistant and Shizuku each have dedicated reading groups below their settings.

- Add a Home Assistant demo with the shared entity picker and live state and detail readings. Document SDK 1 entity reads, subscriptions, availability, reconnection and lifecycle rules.

- Document the expanded SDK 1 device snapshot with RAM, internal storage, IP addresses, battery and display readings, including units, null values and polling guidance.

- Document the SDK 1 device codename, board, manufacturer and ordered ABI fields available through `getDeviceInfo` without root or Shizuku.

- Add a Logo size slider to the DVD screensaver demo, from 50% to 200% with the original size at 100%. Size changes save automatically and keep the logo inside its available viewport.

- Bundle screensaver HTML, CSS, JavaScript, images and fonts under assets and load them on demand through the SDK. Move the DVD demo to separate files, retain its color settings and document asset validation and package limits. Raise the inline HTML limit to 512 KiB.

- Add SDK 1 screensaver rendering and a bouncing DVD Logo demo with Logo color and Background color settings. Document stock settings, lifecycle, document limits and fleet behavior.

- Add an opt-in Shizuku demo group to Hello World with process identity, Android version and kernel version diagnostics. Publish results as live readings, handle unavailable access and ignore stale callbacks without affecting the greeting or chart.

- Add optional SDK 1 Shizuku access, documentation and a separate read-only identity example.

- Demonstrate live readings in KS with numeric values, confirmed states, a history sample count and multiline text. Document automatic display of existing SDK 1 entities without an ESPHome connection.

- Document SDK 1 dashboard URL reads and change notifications, with component redaction and an updated read-only example.

- Add SDK 1 writable switches with boolean commands and confirmed states. Hello World exposes its chart toggle and the entity guide documents the protocol limits and migration steps.

### Changed

- Update the plugin description to explain that Hello World showcases SDK capabilities and serves as a starting point for other plugins.

## 1.2.0

### Fixed

- Take release package versions from the GitHub tag automatically. Generate matching manifests and package filenames without requiring a source manifest version bump. Local builds can use `--version` too.

- Handle dotted Android platform directories such as `android-37.0` without crashing. Release builds explicitly select Android 35 and tests cover mixed platform installations.

### Changed

- Lead the README with the plugin SDK, documentation and getting started steps before introducing the Hello World template.

- Document automatic setting saves and the on-device text edit dialog.

- Document the entry row update check and info modal, automatic stop and resume during updates and rollback after failed activation.

- Rename the settings feature to Plugin Manager and hide its follow-up controls while the master switch is off.

### Added

- Support SDK 1 grouped bar charts in regular and mini layouts and add a Chart type selector to Hello World.

- Add SDK 1 numeric, text and binary sensors plus writable selects, with validated metadata, unknown readings and confirmed select callbacks.
- Demonstrate all four entity types in Hello World and document repository update steps.

- Add SDK 1 regular and mini chart publication and removal with bounded history, sample inspection and an existing-repository migration guide.
- Expand Hello World to showcase every settings control, groups and a live simulated chart with a sampler that stops with its session.

- Consolidate all features into the first public SDK 1. Document KS reads, passive events and transient controls and add a buildable screen and screensaver observer.
- Add a release-triggered GitHub Actions build and document required Actions asset publication and developer-only ZIP testing.

- Document reusable plugin actions for gestures, optional drawer shortcuts and optional Home Assistant buttons.

- Document SDK 1 native files, rich settings, runtime status and RGB entities and vendor host interfaces.

- Document the persistent Enable Plugins master switch, paused plugin behavior and state commands.
- Document local ZIP testing through the Developer Tools group on the kiosk and remote admin.

## 1.0.1

### Changed

- Use `kiosk-satellite-plugin.json` as the single manifest in the repository, release assets and plugin ZIP.
- Discover plugins through the latest stable GitHub release with a separate checksum and README from the release tag.
- Consolidated plugin installation and SDK documentation in the Hello World repository.
- Included the SDK consistency check alongside the template's build and test tools.

## 1.0.0

- Floating Hello World window with a configurable greeting and button counter.
- Settings and commands for the Kiosk Satellite plugin subpage.
- Standalone source repository with compile-time SDK, tests and release descriptor generation.
- GitHub repository installation with a manifest and README preview.
