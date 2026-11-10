# Plugin sensors, selects, switches and numbers

SDK 1 plugins can publish numeric sensors, text sensors, binary sensors, selects, switches and numbers. Add `entities` to the manifest's capabilities. Their current values appear automatically in **Readings** on the plugin subpage, both on-device and in Remote Admin. Local readings work without ESPHome or Home Assistant.

To expose these entities to Home Assistant too, enable ESPHome and native entities in KS and connect Home Assistant. Users can exclude individual plugin entities in the ESPHome entity picker. Exclusions affect Home Assistant exposure and do not hide local readings.

Numeric, text and binary sensors are read-only. A **switch** is a writable boolean control. A **select** is a writable control with a fixed set of advertised options. A **number** is a writable value between a minimum and a maximum, shown in Home Assistant as a slider or an input box. Use a text sensor when a value such as the connection type is observed rather than chosen. Readings are read-only, including the confirmed states of selects, switches and numbers. Publishing an entity does not add an editable setting to the plugin subpage. Declare a setting separately if you want a local control too.

## Readings in the plugin subpage

KS displays each published entity's name and current value in a rounded **Readings** section above charts and settings. Numeric readings respect `accuracyDecimals` and show their unit in quieter text. A number shows as many decimals as its step has. Booleans display **On** or **Off**. Selects show the confirmed option. A null state displays **No data** and an empty string displays **Empty**, so neither is confused with zero or false. Very large numbers use scientific notation.

Text readings preserve line breaks and wrap long content. Use a multiline text sensor for short summaries such as a top-process list. Keep independent measurements in separate sensors so KS can align their labels, values and units. `host.status()` remains a short overall status or error message and preserves line breaks on both surfaces.

Rows follow publication order. Updating a value does not reset a settings edit or chart selection. Removing an entity removes its row. Stopping or disabling the plugin clears its readings and an empty section is hidden. Values belong to the running session and are not persisted as history. Action buttons and RGB lights are not included in this scalar readings section.

Existing plugins using these publication methods need no code changes to display readings. Update KS to a build with the Readings UI and keep `apiVersion: 1`. No new SDK methods or manifest fields are required. Hello World demonstrates numeric precision, units, boolean and select states and multiline text.

## Publish readings

```java
Map<String, Object> metadata = new LinkedHashMap<>();
metadata.put("unit", "dBm");
metadata.put("deviceClass", "signal_strength");
metadata.put("stateClass", "measurement");
metadata.put("accuracyDecimals", 0);
host.publishSensor("rssi", "WiFi signal", metadata, -65.0);
host.publishTextSensor("connection", "Connection type", "WiFi");
host.publishBinarySensor("connected", "Connected", "connectivity", true);
```

Use `null` when a reading is unknown. The bridge sends ESPHome's missing-state flag, preserving the distinction between missing data, zero, an empty string and `false`. Do not use NaN or infinity to signal a missing reading.

| Method | Arguments |
| --- | --- |
| `publishSensor(key, name, metadata, state)` | `Map<String, Object>` metadata and a nullable `Double` state |
| `publishTextSensor(key, name, state)` | Nullable `String` state, at most 512 characters |
| `publishBinarySensor(key, name, deviceClass, state)` | Device class string, or `""` for none, and a nullable `Boolean` state |
| `publishSelect(key, name, options, state)` | `String[]` options and a nullable `String` current selection |
| `publishSwitch(key, name, state)` | Confirmed primitive `boolean` state. Null is not supported |
| `publishNumber(key, name, metadata, state)` | `Map<String, Object>` metadata and a nullable `Double` current value |
| `removeSwitch(key)` | Remove this plugin session's switch |
| `removeSensor(key)` | Remove this plugin session's numeric sensor |
| `removeTextSensor(key)` | Remove this plugin session's text sensor |
| `removeBinarySensor(key)` | Remove this plugin session's binary sensor |
| `removeSelect(key)` | Remove this plugin session's select |
| `removeNumber(key)` | Remove this plugin session's number |

Numeric sensor metadata accepts only these fields:

| Field | Default | Contract |
| --- | --- | --- |
| `unit` | `""` | String of up to 32 characters, such as `ms`, `%` or `dBm` |
| `deviceClass` | `""` | Home Assistant device class, such as `temperature` or `signal_strength`. Empty means none |
| `stateClass` | `"none"` | `none`, `measurement`, `total_increasing` or `total` |
| `accuracyDecimals` | `0` | Integer from 0 to 6, used as a display precision hint |

Device class strings must be empty or match `[a-z][a-z0-9_]{0,39}`. Choose a class and unit appropriate to the measurement. KS validates the format rather than maintaining a copy of Home Assistant's complete class list. Numeric values must be finite and remain finite when converted to an ESPHome 32-bit float. The transport can round values to float precision. Display precision does not change the measured value.

## Writable selects

```java
host.publishSelect("mode", "Performance mode",
    new String[] {"Auto", "Performance", "Efficiency"}, "Auto");
```

KS delivers a requested change through the existing serialized callback:

```java
@Override
public void onEvent(String event, Map<String, Object> payload) {
    if (event.equals("select.mode")) {
        String option = (String) payload.get("option");
        // Apply the choice in your plugin, then publish the actual result.
        applyMode(option);
        host.publishSelect("mode", "Performance mode",
            new String[] {"Auto", "Performance", "Efficiency"}, option);
    }
}
```

`applyMode` above represents your plugin's own implementation. Options must contain 1 to 32 unique, nonempty strings of at most 80 characters each. A non-null published selection must match an option exactly. Requests for an unadvertised option or a removed select are rejected before reaching the plugin. Sensor entities reject commands entirely.

There is no optimistic state change. After applying a choice, publish the resulting selection. If the operation fails, report the failure with `status` and keep or republish the actual state. The callback still has a three-second deadline, so start longer work on a plugin-owned worker and publish the confirmed result when it completes.

A select does not automatically write settings. To retain a plugin-owned choice, call `saveSettings` with the complete validated settings map. Hello World's `select.pattern` handler updates its own Pattern setting, saves it and republishes the selection. Home Assistant and the plugin subpage then show the same choice. This API does not allow a plugin to edit saved KS settings or control another plugin's entities.

## Writable switches

```java
host.publishSwitch("enabled", "Feature enabled", actualEnabled);

@Override
public void onEvent(String event, Map<String, Object> payload) {
    if (event.equals("switch.enabled")) {
        boolean requested = (Boolean) payload.get("on");
        // Apply the request and read the actual state before confirming it.
        boolean applied = applyEnabled(requested);
        host.publishSwitch("enabled", "Feature enabled", applied);
    }
}
```

`actualEnabled` and `applyEnabled` represent your plugin's own state and implementation. The signature is `publishSwitch(String key, String name, boolean state)`. Requests arrive as `onEvent("switch.KEY", {"on": boolean})`. KS rejects non-boolean requests and commands for missing or stopped switches. It does not change the advertised state until the plugin publishes the applied result. Both `true` and `false` are valid states and commands.

[ESPHome's switch protocol](https://github.com/esphome/esphome/blob/dev/esphome/components/api/api.proto) has no missing-state flag. Switches require a known boolean, so wait until you have read the actual state before publishing one. Do not substitute `false` for an unknown reading. `removeSwitch(key)` withdraws the control from the active catalog and rejects further commands. It does not publish an unknown state or delete Home Assistant history or registry entries.

The same three-second callback deadline and explicit confirmation rules as selects apply. Catch expected operation failures and report them with `status` while retaining or republishing the actual state. Run longer operations on a plugin-owned worker. To persist a plugin setting, call `saveSettings` with its complete validated settings map. This switch API does not grant access to saved KS settings. Use a declared action button for one-time operations such as reloading a page.

Hello World's **Demo chart** switch controls the same **Show demo chart** setting used by its subpage. Turning it off removes the chart, stops new chart samples and updates the demo sensor states. The switch remains available so Home Assistant can turn it back on.

## Writable numbers

```java
Map<String, Object> metadata = new LinkedHashMap<>();
metadata.put("min", 0);
metadata.put("max", 100);
metadata.put("step", 5);
metadata.put("unit", "%");
metadata.put("mode", "slider");
host.publishNumber("noise_volume", "Noise volume", metadata, actualVolume);

@Override
public void onEvent(String event, Map<String, Object> payload) {
    if (event.equals("number.noise_volume")) {
        double requested = ((Number) payload.get("value")).doubleValue();
        // Apply the value, then publish what was applied.
        double applied = applyVolume(requested);
        host.publishNumber("noise_volume", "Noise volume", metadata, applied);
    }
}
```

`actualVolume` and `applyVolume` represent your plugin's own state and implementation. Requests arrive as `onEvent("number.KEY", {"value": double})`. KS rejects a value that is not a finite number or falls outside `min` and `max`, and commands for missing or stopped numbers. It does not change the advertised value until the plugin publishes the applied one. Publish `null` while the value is unknown, the way a sensor does.

Number metadata accepts only these fields:

| Field | Default | Contract |
| --- | --- | --- |
| `min` | `0` | Lowest value. Must be below `max` |
| `max` | `100` | Highest value |
| `step` | `1` | The slider's increment. Above 0 and at most `max` minus `min` |
| `unit` | `""` | String of up to 32 characters, such as `%` or `dB` |
| `mode` | `"auto"` | `auto`, `slider` or `box`. With `auto`, Home Assistant picks a slider for a short range and a box for a long one |

`min`, `max`, `step` and the value must be finite and remain finite as ESPHome 32-bit floats. Home Assistant sends values as floats, so KS rounds a request to float precision before handing it over: a slider at 0.3 arrives as `0.3`, not `0.30000001192092896`. A published value is checked against `min` and `max` at the same precision. KS does not snap requests to `step`. A Home Assistant slider only sends values on the step, but an automation can set any value in the range, so round it in the plugin if it matters.

The same three-second callback deadline and explicit confirmation rules as selects and switches apply. To keep the value across restarts, store it in a plugin setting with `updateSettings` and republish it in `start`.

Older Kiosk Satellite releases throw `UnsupportedOperationException` for `publishNumber`. Catch it and fall back to a select if the plugin supports those releases.

### Replace a two-option select

Update KS to a build with these switch methods and copy the current SDK interfaces into your plugin repository. Keep `apiVersion: 1` and the `entities` capability. Stop publishing the old select and publish a switch with the actual boolean state. Replace the `select.KEY` callback and string comparison with `switch.KEY` and the boolean `on` field, then publish the applied state. Your existing plugin setting can stay as it is if the handler maps the boolean to its validated value.

Reusing the key keeps the control recognizable in your source, but the Home Assistant object ID changes from `plugin_<id>____select_<key>` to `plugin_<id>____switch_<key>`. Update automations and entity exclusions that refer to the old select. KS does not delete the old Home Assistant registry entry. If you make this change within a running session, call `removeSelect(key)` too.

## Identity, updates and lifetime

- Keys match `[a-z][a-z0-9_]{0,39}` and names contain 1 to 80 characters. A key belongs to one entity type within one plugin. Reuse the key on every update.
- Sensor, select, switch and number object IDs use `plugin_<normalized-plugin-id>____<type>_<key>`. Plugin ID hyphens become underscores. The four underscores separate these from existing plugin light and action button IDs. Final Home Assistant entity IDs can differ if the user renames them.
- A session can own up to 32 numeric, text, binary, select, switch and number entities combined. This is separate from the existing four-light limit and declared action buttons.
- At most 64 new-entity publications or removals are accepted per plugin per one-second window. KS copies and validates inputs synchronously. Invalid input, exhausted limits or a stopped session throws `IllegalArgumentException` or `IllegalStateException` and leaves the previous declaration intact.
- Changes are coalesced over 250 milliseconds. Intermediate readings can be skipped. Publish the latest state rather than relying on every update as an event counter.
- Changing only a value sends a state update. It does not reconnect ESPHome or refresh the plugin's settings form. Adding or removing an entity or changing metadata, names, select options or number ranges updates the catalog and uses the existing ESPHome reconnect behavior.
- Current states, including unknown values, are replayed when ESPHome attaches or reconnects.
- Disabling, uninstalling, updating or turning off **Enable Plugins** removes the session's entities from the active catalog. KS sends a missing state for removed sensors, selects and numbers. Home Assistant may retain their entity registry entries. Plugin removal does not delete Home Assistant history or registry entries.
- KS does not persist these readings or declarations. Republish them in `start` and stop all samplers in `stop`. The host is revoked before `stop`, so do not call removal methods from that callback. Old sessions cannot deliver updates or receive new select, switch or number commands.
- Entities, state and plugin-specific exclusions stay local to the kiosk and do not sync through Fleet Management. Home Assistant receives only entities allowed by the kiosk's ESPHome configuration.

## Hello World example

Hello World publishes six entities alongside its chart demo:

| Entity | Type | Behavior |
| --- | --- | --- |
| Simulated wave | Numeric sensor | The latest Wave value in percent, with measurement state class and two display decimals. Unknown when the demo chart is hidden |
| Demo status | Text sensor | `Chart running` or `Chart hidden` |
| Demo chart active | Binary sensor | Whether the demo chart is enabled |
| Demo chart | Switch | Controls Show demo chart and saves the confirmed setting |
| Demo pattern | Select | `Sine` or `Triangle`. Changing it updates the same Pattern setting used in the plugin subpage |
| Demo amplitude | Number | A 0 to 100% slider in steps of 5. Changing it rounds the value to the step and saves the same Amplitude setting used in the plugin subpage |

All values are simulated. See [HelloWorldPlugin.java](../src/me/jxl/kiosk/plugins/hello/HelloWorldPlugin.java) for initialization, state publication, select and number handling and shutdown.

## Update an existing repository

1. Update KS to a build that contains these entity methods.
2. Copy the current SDK interfaces from [`sdk/src/me/jxl/kiosk/plugins/`](../sdk/src/me/jxl/kiosk/plugins) into your repository. Keep `apiVersion: 1` and add `entities` to the manifest's capabilities if it is missing.
3. Publish readings from your sampler with the methods above. Handle `onEvent("select.KEY", payload)` for selects, `onEvent("switch.KEY", payload)` for switches or `onEvent("number.KEY", payload)` for numbers. Update test hosts to implement the methods exercised by your tests.
4. Build and test locally. Verify null readings, repeated updates and shutdown. For writable controls, also verify invalid-request rejection and the confirmed value after a change. Switches require known boolean states and deliver `false` as a valid off request.
5. Test the ZIP through **Developer Tools > Install from ZIP**. A GitHub-installed plugin must be uninstalled before switching to a local ZIP, which deletes its settings. A separate development kiosk avoids disturbing the installed copy.
6. Commit the changes and publish a stable GitHub release tagged `v<version>` with the desired new version. The workflow uses the tag as the package version without requiring a source manifest version edit. Let GitHub Actions build and attach the release assets for repository installation.

## Remote Admin API

Authenticated administrators can call `getPluginReadings` with `{"id": "plugin-id"}` to read the current list of scalar entities. Each entry includes `type`, `key`, `name`, `state` and the metadata accepted by its publication method. Names are the plugin's original labels, without the Home Assistant plugin-name prefix. The command returns an empty list for an unknown plugin or a session with no readings and never refreshes or modifies settings. Fleet credentials cannot call it.
