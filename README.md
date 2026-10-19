<h1 align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/ks_plugin_banner_dark.svg" />
    <source media="(prefers-color-scheme: light)" srcset="assets/ks_plugin_banner_light.svg" />
    <img alt="Kiosk Satellite for Home Assistant" src="assets/ks_plugin_banner_default.svg" width="650" />
  </picture>
</h1>

This repository contains the [Kiosk Satellite](https://github.com/jxlarrea/kiosk-satellite) plugin SDK, documentation and getting started guides. Learn how plugins work, build community features and publish a plugin from your own GitHub repository. It also includes **Hello World**, a working starter template with a floating window over the Home Assistant dashboard.

All documented features use **SDK 1**, the first public plugin SDK. Plugins require a Kiosk Satellite build with SDK 1 support and Android 7.0 or newer.

## Documentation

- [Home Assistant](docs/home-assistant.md): use the standard entity picker, follow live states, call services and subscribe to triggers, events and templates through KS-managed authentication.
- [Creating plugins](docs/creating-plugins.md): SDK interfaces, lifecycle, settings, package format, build tools and publishing.
- [Interacting with Kiosk Satellite](docs/ks-api.md): host methods, state queries, transient controls, passive events, capabilities and lifecycle limits.
- [Installing and managing plugins](docs/installing-plugins.md): installation, settings, trust, updates and the remote API.
- [Screensavers](docs/screensavers.md): custom rendering inside the stock screensaver system, with a bouncing DVD demo.
- [Shizuku](docs/shizuku.md): optional shell or root commands through KS, explicit permission, the Hello World demo and a minimal example.
- [Charts](docs/charts.md): publish line and bar charts, inspect samples and update an existing plugin to use charts.
- [Status tiles](docs/status-tiles.md): put a health verdict on the Remote Admin Overview Status panel.
- [Native overlays](docs/overlays.md): draw Android views over the kiosk, from a small bar to a full screen panel, with the KS theme.
- [Voice skins](docs/voice-skins.md): draw the Voice Satellite overlay in place of the built-in skins, with the voice level and the conversation text.
- [Settings buttons and plugin choices](docs/setup-controls.md): guided setup on the settings page, with buttons that report progress, choices the plugin finds and questions asked partway through.
- [Sensors, selects and switches](docs/entities.md): show live readings in KS, publish them to Home Assistant and handle writable controls.
- [Dashboard URLs](docs/dashboard.md): discover the configured server and current view without duplicating URL settings.
- [SDK source](sdk/src/me/jxl/kiosk/plugins): the `KioskPlugin` and `PluginHost` interfaces and the overlay classes supplied by Kiosk Satellite at runtime.
- [Read-only example](examples/read-only): a buildable plugin that observes screen, screensaver and dashboard state.

## Get started

1. Create your own repository from this template and clone it locally. Each repository contains one plugin.
2. Read the [creating plugins guide](docs/creating-plugins.md) and the [KS interaction reference](docs/ks-api.md) to choose the features and capabilities your plugin needs.
3. Update `kiosk-satellite-plugin.json`, the Java class/package in `src/` and this README for your plugin. Keep the plugin ID stable after publication.
4. Build and test locally using the steps below, then [publish a GitHub release](#publish-your-plugin) when it is ready for users.

### Build and test locally

Install Python 3, JDK 17 or newer and an Android SDK with build-tools. Set `ANDROID_HOME` and `JAVA_HOME` as needed.

```sh
python3 tools/test.py
python3 tools/build.py
```

With the unchanged template, the build creates `dist/hello-world-1.0.1.zip`, its `.zip.sha256` checksum file and a copy of `kiosk-satellite-plugin.json` in `dist/`. The ZIP contains that same manifest. The SDK sources in `sdk/` are compile-time dependencies. Their classes are not included in the package.

For developer testing only, open **Plugin Manager > Developer Tools > Install from ZIP** on the kiosk or Remote Admin and select the ZIP from `dist/`. Confirm that you trust the code, then enable the plugin from its entry row. No GitHub release is needed.

To test an update, install the replacement ZIP directly. KS stops the plugin automatically and restores its enabled state after the update. A normal update does not require an app restart. Compatible settings are retained. If the existing plugin was installed from GitHub, uninstall it before switching to a local build. Uninstalling deletes its settings.

If you also have the Kiosk Satellite source, check that the template's SDK interfaces match the application:

```sh
python3 tools/check-sdk.py /path/to/kiosk-satellite
```

## Hello World starter template

Hello World opens a draggable greeting window over your Home Assistant dashboard. It demonstrates every supported settings control, live readings, a live chart, an Overview status tile, a floating view, a button counter and plugin actions that users can assign to gestures, the kiosk drawer or Home Assistant buttons.

Start with [HelloWorldPlugin.java](src/me/jxl/kiosk/plugins/hello/HelloWorldPlugin.java) and its [manifest](kiosk-satellite-plugin.json), then replace the greeting behavior with your own functionality.

### Install Hello World

1. Open **Settings > Plugin Manager** on the kiosk or **Plugin Manager** in Remote Admin and turn on **Enable Plugins**.
2. Choose **Add plugin**, paste this repository's GitHub URL and choose **Preview**.
3. Read the manifest and this README. Choose **Trust and install** if you trust the code.
4. Enable **Hello World** from its entry row. Return to the dashboard to see the window.

The plugin runs trusted code inside Kiosk Satellite. It can access app data and granted Android permissions. Review the source before enabling it.

### Try the template

Tap the Hello World entry to open its subpage. **Greeting** changes the message. **Show window when enabled** controls whether it opens automatically. Settings save automatically. On the kiosk, tap **Greeting** to edit it in a dialog.

The settings are grouped into **Greeting window**, **Chart demo**, **Home Assistant demo**, **Shizuku demo**, **Status tile demo**, **Screensaver demo** and **List demo**. The chart uses simulated values, not device measurements. Its 120-sample history covers about four minutes and updates every two seconds while **Show demo chart** is on.

| Control | Example |
| --- | --- |
| Text input | **Greeting** changes the floating window message |
| Toggle | **Show window when enabled** opens the window on startup. **Show demo chart** shows or removes the chart |
| Number slider | **Amplitude** changes the height of new simulated samples |
| Selection | **Pattern** chooses a sine or triangle wave for new samples. **Chart type** switches between Line and Bar. **Chart size** switches between Regular and Mini |
| Color picker | **Series color** changes the Wave series color |
| Live readings | Numeric values with units, confirmed boolean and select states and a multiline sample summary |
| Read-only chart | **Simulated activity** compares Wave and Reference with sample inspection as lines or grouped bars in regular and mini layouts |

Chart settings take effect on the next sample. Tap or drag the chart to inspect a sample, or double-tap to follow the latest. See the [chart API](docs/charts.md) to replace the simulated values with your own measurements.

**Show window** reopens a dismissed window and **Hide window** closes it. Assign these actions in Gestures or open their action rows to add drawer shortcuts or Home Assistant buttons. The floating **Say hello** button increments its counter. Disable or uninstall using the controls on its entry row.

The master **Enable Plugins** switch pauses all plugins and closes their windows. It keeps each plugin's enabled choice and settings, so turning it back on resumes the selected plugins.

### Try the live readings

Open **Plugin Manager > Hello World** on-device or in Remote Admin. **Readings** shows the simulated wave, demo status, chart states, selected pattern, history sample count and a multiline summary. Values update every two seconds with the chart. Turn **Show demo chart** off to see **No data** for the wave and **Off** for its states. These readings work without Home Assistant or ESPHome and come from the same entities published below.

### Try the Home Assistant entities

With ESPHome and native entities enabled in KS, Hello World also exposes **Simulated wave** as a numeric sensor, **Demo status** as a text sensor, **Demo chart active** as a binary sensor, **Demo pattern** as a select and **Demo chart** as a switch. The numeric value follows the chart and becomes unknown when the chart is hidden. Changing Demo pattern in Home Assistant updates the plugin's Pattern setting and its next samples. Demo chart controls the same Show demo chart setting used in the subpage. These chart readings are simulated. The optional Shizuku diagnostics below read the device.

Use the ESPHome entity picker to exclude any of these. The [entity guide](docs/entities.md) explains how to publish your own readings and handle select and switch requests.

### Try native overlays

Run the **Show top bar** action from Hello World's subpage, a gesture or a drawer shortcut. A pill slides in at the top of the dashboard with a greeting counter, a slider, a **Say hello** button, a **Full screen** button and a **More** button. **More** opens a small menu under the bar that closes when you tap anywhere else. While the bar shows, the dashboard starts below it instead of underneath. It is a native Android view drawn with the KS theme. Back leaves it alone and **Hide top bar** removes it. **Full screen**, or the **Show full screen overlay** action, opens a card over a dimmed kiosk. Close it with back, its **Close** button or a tap outside the card. **Show edge glow** draws an animated glow along the screen edges, over the screensaver and camera views like the voice overlay, while every tap still reaches the dashboard. Back or **Hide edge glow** removes it. See the [overlay guide](docs/overlays.md) and [OverlayDemo.java](src/me/jxl/kiosk/plugins/hello/OverlayDemo.java).

### Try the status tile

Open the Remote Admin Overview. Its Status panel shows a **Hello World demo** tile after the six built-in tiles, with the plugin's name under its state line. In the **Status tile demo** group, **Tile level** moves the dot between green, amber, red and gray, **Tile text** changes the state line and **Show status tile** removes the tile or brings it back. Tapping the tile opens this subpage. The tile goes away when the plugin is disabled or Plugin Manager is off. See the [status tile guide](docs/status-tiles.md) for the API and its limits.

### Try the DVD screensaver

Enable Hello World, then select **DVD Logo (Hello World)** under **Screensaver > Screensaver mode** or in a schedule entry. KS uses its stock screensaver settings and renders the bouncing DVD logo as the content. In Hello World's **Screensaver demo** group, **Logo size**, **Logo color** and **Background color** save automatically and refresh the active screensaver. The demo loads separate HTML, CSS, JavaScript and SVG files from [assets/dvd](assets/dvd). See the [screensaver guide](docs/screensavers.md) for the rendering API and lifecycle.

### Try hardware keys

The **Hardware keys demo** group subscribes to `device.key` while **Show hardware keys** is on. Press a volume, media or remote button with KS in the foreground. **Last hardware key** under Readings shows the key name, its scan code and a press count. Buttons without an Android key code can show as `UNKNOWN` with their scan code. Each press also fires the **Hardware key** gesture trigger, so a gesture set to **Plugin trigger > Hello World: Hardware key** under **Gestures** runs its action. See [hardware keys](docs/ks-api.md#hardware-keys), [gesture triggers](docs/creating-plugins.md#gesture-triggers) and [KeyDemo.java](src/me/jxl/kiosk/plugins/hello/KeyDemo.java).

### Try the voice skin

Hello World is also a voice skin. With the native Voice Satellite runtime enabled, pick **Hello World** under **Voice Satellite > Appearance > Skin** and press **Preview**, or say the wake word. KS draws none of its own overlay. Hello World dims the screen and draws a ring that swells with your voice and the answer, an arc that turns while the assistant thinks and the conversation under it. With **Overlay mode** set to Docked, the ring and the conversation sit in a card at the bottom of the screen instead. Both looks follow Theme, Background, Text size and Reactive activity bar. The conversation follows the **Show what you said** and **Show the answer** settings. Pick another skin to give the turn back to KS. See [Voice skins](docs/voice-skins.md) and [VoiceSkinDemo.java](src/me/jxl/kiosk/plugins/hello/VoiceSkinDemo.java).

### Try Voice Satellite state

The **Voice Satellite demo** group subscribes to `voice.state` while **Show Voice Satellite state** is on. With the native Voice Satellite runtime enabled, say the wake word. **Voice Satellite state** under Readings moves through listening, processing and responding and back to idle, for Assist turns and realtime conversations alike. See [Voice Satellite state](docs/ks-api.md#voice-satellite-state) and [VoiceDemo.java](src/me/jxl/kiosk/plugins/hello/VoiceDemo.java).

### Try intercom state

The **Intercom demo** group subscribes to `intercom.state` while **Show intercom state** is on. With the intercom on, call this kiosk from another one. **Intercom state** under Readings shows ringing, the call and its end, with the other kiosk's name. See [Intercom state](docs/ks-api.md#intercom-state) and [IntercomDemo.java](src/me/jxl/kiosk/plugins/hello/IntercomDemo.java).

### Try list settings

The **List demo** group shows a list setting and the picker settings. **Shortcuts** lists its rows on the subpage. Tap one to edit it, drag its handle to move it or tap **Add shortcut**. **Opens** decides which field shows next: a **Page** from the dashboard picker, a **Camera view** or **Menu entries**, a nested list that opens inside the same editor. **Favorite page**, **Featured icon** and **Camera view** use the same pickers on their own, and **Camera view** only shows while **Use a camera view** is on. **Shortcuts received** and **Picker values** under List demo readings show what `configure` received. See [pickers, lists and conditions](docs/creating-plugins.md#pickers-lists-and-conditions) and [ListDemo.java](src/me/jxl/kiosk/plugins/hello/ListDemo.java).

### Try the setup controls

The **Setup demo** group talks to a pretend device. Press **Test** to check **Device address**. It reports progress, then the result under the row. Put `fail` in the address to see an error. **Pair** asks first, then asks you to trust the device's fingerprint partway through. Press **Refresh** beside **Device** and the plugin finds three devices one at a time. Pick one and **Device name** fills in by itself. See [Settings buttons and plugin choices](docs/setup-controls.md) and [SetupDemo.java](src/me/jxl/kiosk/plugins/hello/SetupDemo.java).

### Try Shizuku

The **Shizuku demo** group demonstrates real device diagnostics through KS's SDK. Start Shizuku and grant KS access using the **Shizuku access** row, then turn on **Enable Shizuku demo**. It is off by default. The greeting window and chart work without Shizuku.

Choose **Process identity**, **Android version** or **Kernel version** in **Diagnostic**. The plugin runs a fixed read-only command every 10 seconds and shows **Shizuku demo status** and **Shizuku result** under Readings. Changing the selection requests a fresh reading. These values also appear as text sensors in Home Assistant when plugin entities are exposed through ESPHome.

The demo never requests permission, grants permissions or changes Android settings. Missing access, failed commands and lost connections clear the result and show a status message. Turning off the demo stops new commands. Results from a previous selection or stopped session are ignored. See [ShizukuDemo.java](src/me/jxl/kiosk/plugins/hello/ShizukuDemo.java) for asynchronous execution and lifecycle handling.

## Publish your plugin

1. Build and test locally during development.
2. Commit the source, README and manifest. The manifest version is the default for local ZIP builds. It does not need to match the release tag. Keep `apiVersion: 1`.
3. Publish a stable GitHub release tagged with the desired version, such as `v1.2.0`, at that commit. The included GitHub Actions workflow tests and builds the source and attaches the manifest, ZIP and checksum automatically. Wait for it to succeed before sharing the release.
4. Share the public repository URL.

The workflow takes the package version from the tag, removing an optional `v` prefix. It writes that version into both generated manifests and the ZIP filename without editing or committing the source manifest. The release title is only a display label. Local builds can also override the version with `python3 tools/build.py --version 1.2.0`.

KS discovers the latest stable GitHub release, previews its attached manifest and reads the README from the tagged commit. It downloads the ZIP only after confirmation and verifies the checksum and packaged manifest. Drafts and prereleases are excluded.

Normal installation uses **Add plugin** with the repository URL. KS rejects manually attached assets and requires the GitHub Actions uploader plus a matching GitHub SHA-256 digest. This verifies publication through Actions, not the honesty of the workflow code. See [publishing requirements](docs/creating-plugins.md#repository-and-release). **Install from ZIP** is reserved for developers testing local builds.

## License

[Apache-2.0](LICENSE). Kiosk Satellite has its own application license.
