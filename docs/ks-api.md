# Plugin interactions with Kiosk Satellite

SDK 1 is the first public plugin API. Plugins can inspect KS state, observe passive events and use transient controls such as dismissing the screensaver or opening a configured camera view. The host bridge does not edit saved KS settings, except the volume levels `setVolume` changes and the wake word mute `setWakeWordMuted` sets, and does not manage files or change permissions. Plugin-owned windows, settings, charts, actions, sensors, selects, switches, RGB lights and screensaver renderers are also supported.

## Capabilities

Every plugin declares `"apiVersion": 1`. All features below belong to that single public SDK.

| Manifest capability | Host interaction |
| --- | --- |
| `overlay` | Show or update the plugin's floating window and [native overlays](overlays.md) |
| `screensaver` | Register rendering content inside the stock screensaver system |
| `screensaver.mode` | Choose which screensaver shows while the plugin runs. See [Switch screensavers](screensavers.md#switch-screensavers) |
| None | Hide its window, log, publish runtime status and charts, save its own settings and receive declared actions |
| `native` | Locate its verified packaged JNI library and DEX container |
| `entities` | Publish and remove its own RGB lights, sensors, selects and switches |
| `host.read` | Execute read commands and subscribe to passive events |
| `host.control` | Execute the transient controls listed below |
| `noise` | Read the [room noise level](#room-noise-level). Requires `host.read` |
| `ha` | Call Home Assistant services, run other commands and subscribe to triggers, events and templates. See the [Home Assistant guide](home-assistant.md#call-services-and-other-commands) |
| `voice.skin` | Appear in Voice Satellite's Skin list and draw the turn in place of the KS overlay. Requires `overlay` and `host.read`. See [Voice skins](voice-skins.md) |
| `voice.text` | Read what the user said and the assistant answered. Requires `host.read`. See [Conversation text](voice-skins.md#conversation-text) |
| `camera` | Receive frames from the device camera. See [Camera frames](camera.md#camera-frames) |
| `barcode` | Open the KS scanner and read a barcode or a QR code. See [Scan a code](camera.md#scan-a-code) |
| `ma` | Run Music Assistant commands for players, queues and the library and follow its events. See the [Music Assistant guide](music-assistant.md#run-commands) |

Request only the capabilities the plugin uses. `getHostApi` is available with either host capability and lists the commands and events available to that session. Plugins execute inside the app and are not sandboxed from arbitrary Java or native code. These capabilities describe the public API contract. Users must trust installed code.

## Read commands

```java
host.executeCommand("isScreensaverActive", Collections.emptyMap(), (ok, data, error) -> {
    if (ok) {
        boolean active = Boolean.TRUE.equals(data);
        host.status(active ? "Screensaver active" : "Screensaver idle", false);
    } else {
        host.status(error, true);
    }
});
```

`executeCommand(String command, Map<String, Object> arguments, PluginHost.CommandCallback callback)` returns immediately. `onResult(boolean ok, Object data, String error)` runs later on the plugin's serialized callback worker. Data is a detached JSON-compatible value: a map, list, string, number, boolean or null. Check `ok` before reading it. On success `error` is null. Missing platform support or an unavailable feature can produce a null reading or a failed result.

Except for `getBrightness`, `getVolume` and `getHaEntityState`, every read command requires an empty arguments map. Names are case-sensitive. The read and control tables form the complete command allowlist. A new core KS command does not automatically become available to plugins.

| Command | Result |
| --- | --- |
| `getHostApi` | `{apiVersion: 1, capabilities: [...], commands: [...], events: [...]}` listing this host's supported names |
| `isScreensaverActive` | Boolean indicating whether the screensaver is active |
| `getScreensaverDue` | Next idle deadline as an ISO 8601 UTC string, or null while no countdown is armed |
| `getScreensaverSuppressed` | Boolean. Currently true because KS owns the screen and suppresses the dashboard's separate screensaver |
| `isScreenOn` | Boolean representing KS's logical screen state |
| `getBrightness` | Brightness from 0 to 1, or null if unavailable. `{}` reads the level controlled by the Screen light. `{"panel": true}` reads current panel brightness. `{"ceiling": true}` reads the adaptive brightness ceiling. Both flags accept booleans and cannot both be true |
| `getAmbientDisplay` | Boolean indicating whether KS has detected that the device leaves an ambient display lit after screen-off |
| `getVolume` | Volume percentage from 0 to 100. `{}` reads the master volume, the device's media volume. `{"channel": "media"}`, `"assistant"` or `"intercom"` reads that share of the master volume, as set in KS's Audio Volume settings. `"master"` is also accepted |
| `getLightLevel` | `{present, lux, live}`. Sensor availability, latest lux reading and whether readings are live. Lux can be null |
| `getStats` | `{battery, charging, cpu, cpuClock, temp, batteryTemp}`. Battery percentage, external power connected, CPU usage percentage, average CPU clock in MHz, CPU temperature in Celsius and battery temperature in Celsius. Unavailable numeric readings can be null. `cpu` is null on a device that does not let an app measure CPU load. `battery` and `batteryTemp` are null on a device without a battery |
| `getUptime` | `{app, network}` in seconds. Network is null while offline |
| `getDashboardState` | `{homeAssistantUrl, startUrl, currentUrl, currentPath}`. Sanitized HTTP/HTTPS URLs and the main WebView path. Fields can be null. See the [dashboard URL guide](dashboard.md) |
| `getDeviceInfo` | `{name, model, device, board, manufacturer, abis, os, osVersion, sdkInt, appVersion, buildNumber, buildMode, package, ramFree, ramTotal, storageFree, storageTotal, ip, ipv6, battery, brightness, screenOn, screenWidth, screenHeight, screenDensity}`. Device identity and a live resource, network and display snapshot. See the field definitions below |
| `getMotionEnabled` | Boolean indicating whether camera motion detection is enabled |
| `getFaceEnabled` | Boolean indicating whether camera face detection is enabled |
| `getProximityEnabled` | Boolean indicating whether proximity detection is enabled |
| `getCameraViewState` | `{active, viewId, viewName, focusedCameraId}` for the existing camera overlay. IDs and name can be null. No images or stream URLs |
| `getCameraViews` | A list of `{id, name, cameraCount}`, one per camera view set up in KS, in their order. Pass `id` to `showCameraView`. No servers, stream URLs or credentials |
| `getDashboards` | A list of `{urlPath, title, icon, custom, views}`, one per Home Assistant dashboard, with `views` a list of `{path, title, icon, subview}`. Pass a view's `path` to `haNavigate`. Icons are MDI names or null. `custom` marks KS's Custom Pages entry. Fails while Home Assistant is not set up or cannot be reached |
| `getWakeWordState` | `{available, stopWordAvailable, enabled, active, listening, engine, engineLabel, status, statusLabel, muted}`. Engine names can be null. No audio, model files or internal configuration |
| `getVoiceState` | `{enabled, state, skinSelected, overlay}`. `enabled` is true while the native Voice Satellite runtime is on. `state` is `idle`, `listening`, `processing` or `responding`. `skinSelected` is true while the Skin setting picks this plugin. `overlay` is `hidden`, `full` or `docked`. See [Voice Satellite state](#voice-satellite-state) and [Voice skins](voice-skins.md) |
| `getVoiceAppearance` | `{theme, background, textScale, reactive}`: Voice Satellite's Appearance settings a skin follows. See [Appearance settings](voice-skins.md#appearance-settings) |
| `getVoiceText` | `{command, answer, tools, streaming, announcement}`. Requires `voice.text`. See [Conversation text](voice-skins.md#conversation-text) |
| `getIntercomState` | `{enabled, state, kiosk, dnd}`. `enabled` is true while the intercom is on. See [Intercom state](#intercom-state) |
| `getNoiseLevel` | `{available, dbfs, held, updated}`. Requires `noise`. See [Room noise level](#room-noise-level) |
| `getHaEntityState` | `{entityId: string}` arguments. Returns `{entityId, status, state, attributes, lastChanged, lastUpdated}`. Read-only HA entity snapshot. See the [Home Assistant guide](home-assistant.md) |
| `haStatus` | `{configured, connected}` booleans reflecting KS's existing Home Assistant connection check. This does not initiate a new connection check |
| `getSendspinPlayer` | `{enabled, running, connected, clientId, playerName, serverName, maConfigured, maPlayerId, maServerId, maServerName, maServerVersion}`: the kiosk's own Sendspin player and the Music Assistant player that stands for it. See [The kiosk's player](music-assistant.md#the-kiosks-player) |
| `getLauncherApps` | A list of `{package, label}`, one per app the plugin can open with `launchApp`, in the App Launcher's order. See [Open an app](#open-an-app) |

Object responses expose only the fields listed above. A field absent from the underlying feature is returned as null. KS failure details are reduced to a generic SDK error rather than exposing internal responses. Reads use the existing feature state and queries. They do not request Android permissions or enable disabled features.

### Device snapshot

`getDeviceInfo` returns the configured device `name`, the existing `model` display string and these additional readings using `host.read`:

| Field | Value |
| --- | --- |
| `ramFree`, `ramTotal` | Available and total system RAM in bytes. Available RAM includes memory Android can reclaim, matching KS's device details |
| `storageFree`, `storageTotal` | Available and total bytes on Android's internal data partition. This is not removable storage or the app's storage usage |
| `ip` | First non-loopback IPv4 address excluding link-local addresses, or null when unavailable. This is an interface address, not a public IP lookup or a guarantee of the current default route |
| `ipv6` | List of non-loopback IPv6 addresses across interfaces, including link-local addresses. Empty when none are available |
| `battery` | Charge percentage from 0 to 100, or null when unavailable or the device has no battery |
| `brightness` | Current panel brightness setting from 0 to 1, equivalent to `getBrightness` with `{"panel": true}`. Includes KS's window override when active. This is not the adaptive brightness ceiling or a physical luminance measurement |
| `screenOn` | KS's logical screen status, equivalent to `isScreenOn`. This does not guarantee that manufacturer hardware has fully powered the panel off |
| `screenWidth`, `screenHeight` | Full display dimensions in pixels as reported by Android. These are not the plugin window dimensions or physical inches |
| `screenDensity` | Android's logical display density scale, such as 1.0 or 2.0. This is not DPI |

Unavailable resource and display readings are null. These values are collected on demand and may change during the read. The response is not an atomic system snapshot. For frequent updates, keep using `getStats`, `getBrightness` and `isScreenOn` rather than repeatedly collecting all device details and network addresses.

Network addresses are now included in the existing `host.read` capability. No root, Shizuku or additional permission prompt is required. Fields outside this documented response, such as Android build fingerprints, remain filtered out.

### Android hardware identity

`getDeviceInfo` includes raw Android build metadata through the existing `host.read` capability. It does not require root, Shizuku or an additional Android permission. SDK `apiVersion` remains 1.

| Field | Value |
| --- | --- |
| `model` | Existing display string combining manufacturer and model. Do not split it to identify hardware |
| `device` | Android `Build.DEVICE`, the product codename, such as `rk3576_u` |
| `board` | Android `Build.BOARD`, the board identifier. This is the vendor's reported value, not a normalized SoC name or `Build.HARDWARE` |
| `manufacturer` | Android `Build.MANUFACTURER` without the model appended |
| `abis` | List of Android `Build.SUPPORTED_ABIS` in preference order, such as `["arm64-v8a", "armeabi-v7a", "armeabi"]` |
| `sdkInt` | Existing Android API level, useful alongside `abis` when choosing compatible native packages |

Before Android metadata is initialized or on another platform, `device`, `board` and `manufacturer` are null and `abis` is empty. A 64-bit processor running 32-bit Android reports the ABIs supported by that Android installation. Use `abis` to select a compatible build rather than guessing from the board or model.

## Transient controls

Declare `host.control` and call the same asynchronous `executeCommand` method:

```java
host.executeCommand("stopScreensaver", Collections.emptyMap(), (ok, data, error) -> {
    if (!ok) host.status(error, true);
});
host.executeCommand("showCameraView", Collections.singletonMap("viewId", "front-door"),
    (ok, data, error) -> { if (!ok) host.status(error, true); });
```

| Command | Arguments and behavior |
| --- | --- |
| `startScreensaver` | `{}`. Start the configured screensaver |
| `stopScreensaver` | `{}`. Dismiss it, wake the display and reset the idle timer |
| `postponeScreensaver` | `{}`. Report activity to reset the idle timer and dismiss an active screensaver |
| `nextScreensaverSlide` | `{}`. Step forward in the active slideshow or camera rotation. Returns whether anything stepped |
| `previousScreensaverSlide` | `{}`. Step backward. Returns whether anything stepped |
| `screenOn` | `{}`. Wake the display |
| `screenOff` | `{}`. Turn off the display using an existing permission. Fails quietly when permission is missing and never opens a grant prompt |
| `showCameraView` | `{viewId: string, toggle?: boolean}`. Open an existing configured view. `toggle: true` closes that view if already open |
| `hideCameraView` | `{}`. Close the current camera overlay |
| `focusCamera` | `{cameraId: string}`. Focus an existing camera in the active view. Empty string or `{}` returns to its grid |
| `showNowPlaying` | `{}`. Show full-screen Now Playing when enabled and a track is loaded |
| `hideNowPlaying` | `{}`. Dismiss full-screen Now Playing. Playback and saved preferences stay unchanged. Normal idle behavior can show it again later |
| `sendspinControl` | `{command: "play" | "pause" | "next" | "previous"}`. Control the current Sendspin group or followed player. Does not edit queues or playlists |
| `setVolume` | `{percent: string, channel?: string}`. Set a volume from `"0"` to `"100"`. Decimals are allowed. Without a channel, or with `"master"`, it sets the device's media volume. `"media"`, `"assistant"` and `"intercom"` set the matching KS Audio Volume slider, which is saved like a change made in Settings. The master volume has the device's own steps, often 15, so a small change can round to the current level |
| `setWakeWordMuted` | `{muted: boolean}`. Mute or unmute wake word detection, the same mute as Voice Satellite's Mute switch in Home Assistant. Returns the muted state it set. With Voice Satellite running in the app, KS sets it without Home Assistant. With Voice Satellite running in the dashboard, KS turns the integration's Mute switch through Home Assistant, so `wakeword.state` follows once it applies. Fails while Voice Satellite is off |
| `showAppLauncher` | `{}`. Open the configured launcher when enabled and apps exist |
| `hideAppLauncher` | `{}`. Close the launcher overlay |
| `launchApp` | `{package: string}`. Open an app from the App Launcher over the kiosk. Fails for any other app. See [Open an app](#open-an-app) |
| `showOverlayPage` | `{url: string}`. Open an HTTP or HTTPS page over the dashboard without a close button |
| `showLinkPage` | `{url: string}`. Open an HTTP or HTTPS page with a close button |
| `hideOverlayPage` | `{}`. Close the web overlay, including Music Assistant |
| `showMusicAssistant` | `{}`. Open the configured Music Assistant interface |
| `openCalendar` | `{view?: "day" \| "week" \| "month" \| "schedule", date?: string}`. Open the full screen calendar, waking the display and dismissing the screensaver. `date` is the day to open on, as `yyyy-MM-dd`. Without them it opens on the default view from **Settings > Calendar** and today. If the calendar is already open, it moves to the view and day asked for |
| `hideCalendar` | `{}`. Close the calendar |
| `loadUrl` | `{url: string}`. Navigate the main WebView to HTTP or HTTPS. Does not change the saved Start URL |
| `loadDashboard` | `{dashboard: string}`. Navigate to a dashboard path on the configured HA server |
| `loadStartUrl` | `{}`. Navigate to the saved Start URL |
| `haNavigate` | `{path: string}`, such as `lovelace/0` or `cameras/0?camera=garage`. Navigate to a dashboard view and dismiss obstructing overlays. Returns true once the page is on the view, including when it already was, and false when it could not move, for example while a page that is not Home Assistant is on screen. The result can take a few seconds while KS checks that the view rendered |
| `reload` | `{}`. Reload the current dashboard page without clearing browser data |

Controls return null on success unless the table documents a boolean result. Missing configuration, unsupported media commands and normal KS feature gates can cause failure. URLs must not contain credentials or use executable schemes such as `javascript:`. Dashboard paths accept slash-separated letters, digits, underscores and hyphens. `haNavigate` paths also accept dots, a query string and a fragment, but no `.` or `..` segments. Unknown arguments are rejected.

These commands change live presentation or playback without editing saved configuration or content. Navigation can leave an unfinished form and screen-off can interrupt someone using the device. Authors should make these behaviors intentional. The usual screensaver session bookkeeping and idle behavior still apply. Plugins cannot take ownership of shared internal voice suppression or screen wake-lock flags.

### Open an app

`launchApp` opens only the apps added in **Settings > App Launcher > Apps**, never any other app on the device. `getLauncherApps` lists them, so a plugin can offer them with [plugin choices](setup-controls.md#plugin-choices) instead of asking for a package name. Kiosk Mode restrictions apply:

- While the App Launcher is off or Lockdown Mode is on, the list is empty and `launchApp` fails.
- While Kiosk Mode is on, the list holds only the apps under **Settings > Kiosk Mode > Allowed Actions > Allowed Apps**. It is empty when the **Apps** action is not allowed.

The app opens with the kiosk running behind it, the same as from the App Launcher. The App Launcher's **Return automatically** setting brings the kiosk back. Read the list again before showing it, since the user can change these settings at any time.

```java
host.executeCommand("launchApp", Collections.singletonMap("package", "com.spotify.music"),
    (ok, data, error) -> { if (!ok) host.status(error, true); });
```

## Events

```java
host.subscribe("screensaver.state");

// In KioskPlugin.onEvent:
if (event.equals("ks.screensaver.state")) {
    boolean active = Boolean.TRUE.equals(payload.get("active"));
    host.status(active ? "Screensaver active" : "Screensaver idle", false);
}

// While still running, if the plugin no longer needs this event:
host.unsubscribe("screensaver.state");
```

Pass the subscription name from this table to `subscribe` or `unsubscribe`. Delivery uses `onEvent("ks." + name, payload)`. Every payload also contains `time`, an ISO 8601 UTC string recording when the SDK observed the event. Subscriptions are idempotent, apply only to the current plugin session and do not replay earlier events. Subscribe first and use read commands for initial state. Responses and subsequent events can race, so prefer the latest event if a read was requested before it.

| Subscription name | Payload fields besides `time` |
| --- | --- |
| `screensaver.state` | `active`: boolean |
| `screensaver.countdown` | `due`: ISO 8601 UTC string or null |
| `screensaver.view` | `view`: active overlay mode string or null. Dim mode has no overlay |
| `screen.state` | `on`: boolean, `source`: `app`, `system` or `probe` |
| `screen.brightness` | `level`: configured control level, `panel`: actual panel level. Both normalized to 0 through 1 |
| `screen.ambient` | `on`: boolean indicating detected ambient display behavior |
| `device.power` | `charging`: boolean indicating external power connected |
| `device.network` | `up`: boolean for the default network |
| `device.volume` | No additional fields. Fires when the master volume or the media, assistant or intercom share changes. Read `getVolume` for each channel you need |
| `device.light` | `lux`: ambient light reading |
| `device.key` | `key`: Android key name without the `KEYCODE_` prefix, such as `VOLUME_UP`. `code`: Android key code. `scanCode`: hardware scan code. `action`: `down` or `up`. `repeat`: repeat count while held. See [hardware keys](#hardware-keys) |
| `detection.motion` | No additional fields |
| `detection.face` | No additional fields |
| `detection.proximity` | `held`: false on approach, true while still near |
| `detection.person` | `held`: false on arrival, true while presence continues |
| `detection.presence` | `present`: boolean from the device person sensor |
| `voice.interaction` | `active`: boolean, `source`: `page`, `sendspin` or `command`. No speech or conversation content |
| `voice.state` | `state`: `idle`, `listening`, `processing` or `responding`. See [Voice Satellite state](#voice-satellite-state) |
| `voice.overlay` | `overlay`: `hidden`, `full` or `docked`, as the voice overlay shows. See [Voice skins](voice-skins.md#when-to-draw) |
| `voice.level` | `level`: 0 to 1, the level the reactive bar follows. 0 while Reactive activity bar is off. No audio. See [Voice level](voice-skins.md#voice-level) |
| `voice.skin` | `selected`: true while the Skin setting picks this plugin. See [Voice skins](voice-skins.md) |
| `voice.appearance` | `theme`, `background`, `textScale` and `reactive`. See [Appearance settings](voice-skins.md#appearance-settings) |
| `voice.text` | `command`, `answer`, `tools`, `streaming` and `announcement`. Requires `voice.text`. See [Conversation text](voice-skins.md#conversation-text) |
| `intercom.state` | `state`, `kiosk` and `dnd`. See [Intercom state](#intercom-state) |
| `wakeword.state` | `active`, `listening` and `muted`: booleans |
| `wakeword.detected` | `model` and `phrase`: wake-word identifiers. No captured audio |
| `stopword.detected` | No additional fields |
| `browser.state` | No additional fields. The main WebView URL or saved HA/Start URL changed. Read `getDashboardState` for the current snapshot |
| `camera.view` | `active`, `viewId`, `viewName` and `focusedCameraId` |
| `sendspin.player` | The `getSendspinPlayer` fields. Fires when the player turns on or off, connects or disconnects, or Music Assistant names its player |
| `audio.noise` | `available`, `dbfs` and `held`. Requires `noise`. See [Room noise level](#room-noise-level) |

Events are passive observations. Motion, face, person, proximity and wake-word events only exist when the corresponding KS feature is already producing them. A subscription never starts a camera, opens a microphone or changes the screensaver policy. No general event-bus subscription is provided.

Delivery is best effort. KS coalesces repeated events of the same type over a 100 ms window, about 33 ms for `voice.level`, and keeps only the latest pending payload of each type while the plugin is busy. Do not use these events as an audit log or exact occurrence counter.

### Hardware keys

`device.key` reports the hardware keys that reach KS while it is in the foreground, such as volume, media, D-pad and remote buttons. A button without an Android key code can arrive as `UNKNOWN` with its `scanCode`. Printing and modifier keys are never reported, so typed text does not reach plugins. Keys the system handles before any app, such as power and home, are not reported either, and nothing arrives while another app is in front or the screen is off.

The event is a passive observation. KS still handles the key as usual, so a plugin that controls volume itself should be paired with **Disable volume buttons** in Kiosk mode. KS then stops the system volume change and the plugin still receives every press.

Key events are not coalesced. Each down, up and repeat arrives as its own event. Up to 16 presses are queued while the plugin is busy and later presses are dropped until it catches up.

```java
host.subscribe("device.key");

// In KioskPlugin.onEvent:
if (event.equals("ks.device.key") && "down".equals(payload.get("action"))) {
    host.status("Pressed " + payload.get("key"), false);
}
```

To let a press run any action the user picks under **Gestures**, declare a [gesture trigger](creating-plugins.md#gesture-triggers) and call `host.fireTrigger` from the press. No `host.control` is needed for that.

With `host.control`, a plugin can turn the presses into volume changes. Read the current level with `getVolume`, keep it up to date from `device.volume` and write the new one with `setVolume`:

```java
// level holds the last getVolume result for the assistant channel.
if ("VOLUME_UP".equals(payload.get("key"))) {
    Map<String, Object> arguments = new HashMap<>();
    arguments.put("channel", "assistant");
    arguments.put("percent", String.valueOf(Math.min(100, level + 10)));
    host.executeCommand("setVolume", arguments, (ok, data, error) -> {
        if (!ok) host.status(error, true);
    });
}
```

### Voice Satellite state

`voice.state` follows this kiosk's own Voice Satellite turns, the same value as the **Voice Satellite** sensor on its ESPHome device. Use it to drive an LED ring or any other indicator without asking the user to pick an entity.

| State | Meaning |
| --- | --- |
| `idle` | No turn in progress |
| `listening` | The microphone is capturing the request |
| `processing` | The request is with the assistant |
| `responding` | The answer or an announcement is playing |

It covers realtime conversations too. A realtime conversation runs no Assist pipeline, so Home Assistant's `assist_satellite` entity stays at `idle` through it while `voice.state` moves through listening and responding as the conversation goes. Following `voice.state` also needs no Home Assistant connection and does not break when the device or its entities are renamed.

The state comes from the native Voice Satellite runtime. While it is off, `getVoiceState` returns `enabled: false` with `state: "idle"` and no events arrive. The event only fires on changes, so read `getVoiceState` once after subscribing. A change can arrive before the read returns, so let an event win over an older read. No speech, transcript or answer text is included. A plugin that declares `voice.text` can read the words with [`getVoiceText`](voice-skins.md#conversation-text).

```java
host.subscribe("voice.state");
host.executeCommand("getVoiceState", Collections.emptyMap(), (ok, data, error) -> {
    if (ok) showState((String) ((Map<?, ?>) data).get("state"));
});

// In KioskPlugin.onEvent:
if (event.equals("ks.voice.state")) showState((String) payload.get("state"));
```

Older KS versions reject the `voice.state` subscription with `IllegalArgumentException`. See [VoiceDemo.java](../src/me/jxl/kiosk/plugins/hello/VoiceDemo.java) for a complete example that handles both.

### Intercom state

`intercom.state` follows this kiosk's intercom, the same values as the **Intercom**, **Intercom kiosk** and **Intercom do not disturb** entities on its ESPHome device. Use it for a call light or a ring indicator.

| State | Meaning |
| --- | --- |
| `idle` | No call |
| `calling` | This kiosk is ringing another kiosk |
| `ringing` | Another kiosk is ringing this one |
| `in_call` | A call is live |
| `broadcasting` | This kiosk is announcing to every kiosk |
| `listening` | Another kiosk's announcement is playing here |
| `ended` | A call or announcement just ended. Holds for a few seconds before `idle` |
| `missed` | A call nobody answered. Holds for a minute before `idle` |

`kiosk` is the other kiosk's name during a call and the caller's through `missed`, else an empty string. `dnd` is true while Do not disturb holds, from the answer mode or Lockdown Mode.

The event fires only when one of the three values changes. While the intercom is off, `getIntercomState` returns `enabled: false` and no events arrive. Read `getIntercomState` once after subscribing and let an event win over an older read, the same as [Voice Satellite state](#voice-satellite-state). No audio, call IDs or kiosk addresses are included. Older KS versions reject the subscription with `IllegalArgumentException`. See [IntercomDemo.java](../src/me/jxl/kiosk/plugins/hello/IntercomDemo.java).

Entity-specific events use `ha.entity.<entity_id>` with `host.read` and arrive as `ks.ha.entity.<entity_id>`. They include an initial state, live updates and connection status. See the [Home Assistant guide](home-assistant.md) for payloads, limits and subscription cleanup.

### Room noise level

`getNoiseLevel` and `audio.noise` report how loud the room is as one number, so a plugin can lower answers and chimes in a silent house and raise them over the TV with `setVolume`. They need the `noise` capability on top of `host.read`, which tells users the plugin reads the room. The same level feeds the kiosk's **Ambient noise** sensor in Home Assistant.

| Field | Meaning |
| --- | --- |
| `available` | False while wake word detection is not listening or the satellite is muted. `dbfs` is null then |
| `dbfs` | Mean level over 5 seconds as a whole number in dBFS, where 0 is the loudest the microphone records. Null until the first 5 seconds are measured |
| `held` | True while a voice turn, announcement, timer, alarm, media playback or intercom call runs. Also true right after the kiosk played a sound of its own. `dbfs` keeps its last value then |
| `updated` | ISO 8601 UTC time of the latest measurement. `getNoiseLevel` only |

`getNoiseLevel` returns the latest 5 second reading. `audio.noise` fires when the level moves by 2 dB or more and when `held` or `available` changes. The level comes from the capture the wake word engine already has open, so subscribing never opens the microphone. No audio, spectrum or speech detection is exposed. The reading is relative to the microphone and its gain, not a calibrated sound level, so let users set thresholds for their room.

## Lifetime, errors and limits

- Commands require an active SDK 1 session with the matching host capability. Subscriptions require `host.read`. Missing access, invalid native arguments, unknown subscription names or exceeded request limits throw synchronously. Handle these errors in plugin code.
- Unknown commands, invalid command-specific arguments, unavailable features and command failures return `ok=false` through the callback.
- At most eight command callbacks may be pending per plugin. At most 20 commands may start per one-second rate window. Arguments are limited to two fields with boolean or string values. Keys are at most 32 characters and strings at most 2048 characters and responses to 32 KiB of JSON.
- KS commands time out after eight seconds. The Android bridge has a ten-second response deadline. Callback delivery can be later while the plugin worker is occupied. A timed-out command may still finish internally. Its late result is ignored, but a control may already have taken effect. Do not blindly retry controls such as toggle or next.
- Command completions, KS events and existing lifecycle callbacks share the plugin's worker. Each callback must finish within three seconds. Never block a lifecycle callback waiting for a command callback. A callback exception or timeout disables the plugin through the existing failure handling.
- Disabling, uninstalling, updating or turning off Enable Plugins revokes the session. Pending command completions and queued events for it are discarded. A replacement session cannot receive old results, even when it has the same plugin ID.
- Subscriptions are not saved. Subscribe again in `start`. KS revokes access before calling `stop`, so `stop` should release plugin resources and should not call the host API to unsubscribe.
- Command requests are logged with the plugin ID through KS's normal command logging. Existing quiet queries remain quiet. Subscribing does not add polling.

## Other supported host interactions

These methods operate on resources owned by the plugin and are part of SDK 1.

| Method or callback | Contract |
| --- | --- |
| `showWindow(title, message, buttonLabel)` | One draggable plain-text window per plugin above the dashboard. Title is 1 to 80 characters, message at most 4096 and button label at most 80. An empty label hides its button |
| `hideWindow()` | Removes that plugin's window |
| `showOverlay(key, spec, factory)` | With `overlay`, show or replace a native Android view over the kiosk. Up to four per session. See [native overlays](overlays.md) |
| `hideOverlay(key)` | Removes that overlay. Unknown keys are ignored |
| `setContentInsets(left, top, right, bottom)` | With `overlay`, reserve space along the screen edges in dp, so Home Assistant pads its views clear of a bar. See [Keep the dashboard clear](overlays.md#keep-the-dashboard-clear) |
| `isHeadless()` | `true` on a device without a screen, where overlays, the window and screensavers never show. Older Kiosk Satellite versions return `false`. See [Devices without a screen](creating-plugins.md#devices-without-a-screen) |
| `log(message)` | Plugin-prefixed diagnostic log, truncated to 1000 characters |
| `status(message, error)` | Runtime status in the plugin subpage, at most 1000 characters. Not persisted |
| `publishScreensaverAsset(key, title, entry, data)` | With `screensaver`, register an HTML asset and scalar rendering options. Bundled resources load on demand from the verified package. See [screensavers](screensavers.md). |
| `publishScreensaverView(key, title, factory)` | With `screensaver`, register a screensaver drawn by a native Android view. See [Draw it natively](screensavers.md#draw-it-natively) |
| `publishScreensaver(key, title, html)` / `removeScreensaver(key)` | With `screensaver`, register or withdraw rendering content inside the stock screensaver system. See [screensavers](screensavers.md). |
| `publishSeries(key, chart)` | Publish a read-only chart in the plugin subpage. Up to four charts per session with four series and 240 samples each. See the [chart API](charts.md) |
| `removeSeries(key)` | Remove a chart from the active plugin session |
| `saveSettings(values)` | Saves values validated against the plugin's declared settings. Unknown keys are rejected and omitted values use defaults. Does not invoke `configure` again |
| `updateSettings(values)` | Saves only the named settings. Omitted values, secrets included, keep what was saved. Does not invoke `configure` again |
| `setupResult(run, state, message)` | Reports `running`, `success` or `error` for a settings button or choices run. Returns `false` once the run is over. See [settings buttons and plugin choices](setup-controls.md) |
| `confirmSetup(run, title, message, confirmLabel)` | Asks the person who started the run to confirm a step. The answer arrives as `settings.confirm` |
| `setSettingChoices(key, ids, labels)` / `setSettingChoices(key, choices)` | Lists the choices of a `string` setting or list row field declared with `"choices": "plugin"`. The second form takes up to 2000 grouped choices with details and icons. See [settings buttons and plugin choices](setup-controls.md#plugin-choices) |
| `nativeLibraryPath(name)` | Verified per-session path to a packaged library matching the process ABI. Pass `rockchip_led` for `librockchip_led.so` |
| `packagePath()` | Verified plugin DEX JAR path, for a plugin-owned helper |
| `assetPath(name)` | Absolute path to a verified, read-only file under the package's `assets/` folder, such as `fonts/icons.ttf` for `Typeface.createFromFile`. Throws `IllegalArgumentException` for a missing file or a path that leaves `assets/`. No capability is required |
| `openAsset(name)` | Opens the same file as an `InputStream`. Close it when done |
| `publishLight(key, name, effects, state)` | Registers or updates a plugin-owned RGB light. At most four lights, 24 effects per light and bounded names. See [RGB states](creating-plugins.md#rich-settings-and-hardware) |
| `publishSensor`, `publishTextSensor`, `publishBinarySensor` | Publish numeric, text or boolean readings shown on the plugin subpage and exposed through ESPHome. See the [entity API](entities.md) for signatures, metadata and limits |
| `publishSwitch(key, name, state)` | Publish a writable switch with a confirmed boolean state. Commands arrive as `onEvent("switch.KEY", {"on": boolean})`. See the [entity API](entities.md#writable-switches) |
| `publishSelect(key, name, options, state)` | Publish a writable select. Confirm applied changes by publishing its resulting state |
| `removeSensor`, `removeTextSensor`, `removeBinarySensor`, `removeSelect`, `removeSwitch` | Remove the corresponding entity owned by the active session |
| `onEvent("select.KEY", payload)` | A request for an advertised select option, passed as `option`. No automatic setting write or optimistic state change |
| `removeLight(key)` | Removes the plugin-owned light |
| `start(host, settings)` | Receives the host and validated settings when the plugin starts |
| `configure(settings)` | Receives validated saved settings changes |
| `execute(command, arguments)` | Receives a declared action invoked by a gesture, drawer shortcut, ESPHome button or authenticated API. Arguments are currently empty |
| `onEvent("window.action", payload)` | The plugin window's button was pressed. Empty payload |
| `onEvent("window.closed", payload)` | The user dismissed its window. Empty payload |
| `onEvent("overlay.closed", payload)` | Back closed one of its overlays. `key` names it |
| `onEvent("settings.button", payload)` | A settings button was pressed. `id` names it and `run` names the run to report on |
| `onEvent("settings.choices", payload)` | Refresh was pressed on a choices setting. `key` names it and `run` names the run |
| `onEvent("settings.cancel", payload)` | A run ended before the plugin finished it. `run`, `reason`: `canceled`, `replaced` or `timeout` |
| `onEvent("settings.confirm", payload)` | The answer to `confirmSetup`. `run`, `accepted` |
| `onEvent("settings.page", payload)` | Its settings page opened on the kiosk or in any Remote Admin browser, or closed everywhere. `open`. Only changes arrive. See [Know when the settings page is open](setup-controls.md#know-when-the-settings-page-is-open) |
| `onEvent("light.KEY", payload)` | A command for its RGB light, containing the supplied fields from `on`, `brightness`, `red`, `green`, `blue` and `effect` |
| `onEvent("ks.NAME", payload)` | A subscribed SDK 1 event from the table above |
| `stop()` | Ends the session. Release timers, threads and hardware handles |

Settings controls support strings, booleans, numbers, colors, selections, groups, descriptions, buttons and plugin choices. Actions and RGB lights are namespaced by plugin ID. Users opt actions into the drawer and Home Assistant through Plugin Manager. Neither mechanism lets a plugin replace a core KS command.

## Not exposed

The host bridge does not expose edits to saved KS settings, file creation or deletion, kiosk unlock, app launching outside the configured launcher, restarts, installs, permission changes or commands targeting another plugin. It also excludes raw settings, credentials, browser storage, arbitrary JavaScript, camera images, microphone audio, screenshots, notification management, logs and arbitrary Home Assistant calls. A command name beginning with `get` does not grant access. Brightness and volume setters are excluded because the existing handlers persist settings. Internal voice suppression and wake-lock controls are excluded because they modify shared ownership state.

## Buildable example

The [read-only example](../examples/read-only/src/example/kiosk/ReadOnlyPlugin.java) reads initial screen and screensaver state and observes changes. It reports them in its own runtime status without changing KS state.

From this repository:

```sh
python3 tools/build.py examples/read-only
```

Install `examples/read-only/dist/ks-read-only-example-1.0.0.zip` through **Plugin Manager > Developer Tools > Install from ZIP** on an SDK 1 build. Enable the example and open its subpage to see its status. The Hello World template and Rockchip LED Control use the same SDK 1 contract.

## Optional Shizuku commands

The `shizuku` capability adds `shizukuState()` and asynchronous `executeShizuku(command, timeoutMs, callback)`. It requires separate user permission for KS and does not change the `host.read` or `host.control` allowlists. See the [Shizuku guide](shizuku.md) for the complete contract and separate example.
