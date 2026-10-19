# Settings buttons and plugin choices

A plugin that connects to a device or a service usually needs a guided setup: enter the address, test it, find the devices there and pick one. Settings buttons and plugin choices put those steps on the plugin's own settings page, on the kiosk and in Remote Admin alike. The plugin does the work. Kiosk Satellite draws the controls, keeps saved secrets intact and asks the person before anything sensitive happens.

No capability is required. Keep `apiVersion: 1`.

## Buttons

Declare buttons in a top-level `buttons` array. Each one is a row at the end of its settings group, with its title, description and a button.

```json
"buttons": [
  { "id": "testConnection", "title": "Test connection", "label": "Test", "group": "Connection",
    "description": "Checks the address and the token.",
    "visibleWhen": { "key": "host", "notEmpty": true } },
  { "id": "pair", "title": "Pair with the bridge", "label": "Pair", "group": "Connection",
    "confirm": { "title": "Pair now?", "message": "Press the button on the bridge first.", "confirmLabel": "Pair" } }
]
```

| Field | Contract |
| --- | --- |
| `id` | Required. Unique, matching `[a-z][a-zA-Z0-9]*`, up to 64 characters |
| `title` | Required. The row's name, up to 80 characters |
| `label` | Required. The button's text, up to 40 characters |
| `group` | The settings group the row closes. `Settings` when left out. A group may hold only buttons |
| `description` | Up to 400 characters under the title |
| `visibleWhen` | The same condition settings use. It may name any top-level setting except a list or a secret |
| `confirm` | `title` (80), `message` (1000) and `confirmLabel` (40). Kiosk Satellite asks before it tells the plugin |

A plugin can declare up to 20 buttons. They are not actions: they never show up as gestures, drawer shortcuts or Home Assistant buttons. A button is off while the plugin is not running.

A press reaches the plugin as `onEvent("settings.button", {"id": ..., "run": ...})`. A remote text field saves when it loses focus. The press waits for that save, so `configure` already has what the person typed.

## Plugin choices

A `string` setting with `"choices": "plugin"` shows a picker of choices the plugin lists, with a Refresh button beside it. The setting stores the choice's ID, a stable value the plugin understands. The picker shows its label.

```json
{ "key": "light", "type": "string", "title": "Light", "default": "", "choices": "plugin",
  "group": "Connection", "visibleWhen": { "key": "host", "notEmpty": true } }
```

Only top-level `string` settings take plugin choices. List them with `setSettingChoices`:

```java
host.setSettingChoices("light", new String[] {"lamp-1", "lamp-2"}, new String[] {"Kitchen", "Hallway"});
```

- At most 200 choices. IDs are unique and 1 to 512 characters. Labels are up to 80 characters. Pass `null` as the labels array or as one label to show the ID.
- Each call replaces the list. The list lasts for the plugin session, so list known choices again in `start` if the plugin keeps them.
- A saved ID the list lacks stays saved. The picker shows it with a warning and the line "This choice is no longer listed."
- Refresh reaches the plugin as `onEvent("settings.choices", {"key": ..., "run": ...})`. Look again, list what you find and report the outcome.

## Runs

Every press and every refresh starts a run. Kiosk Satellite owns the run and names it in the event's `run` field. Report on it with `setupResult`:

```java
host.setupResult(run, "running", "Found 2 of 5");
host.setupResult(run, "success", "Connected to Bridge v2.");
host.setupResult(run, "error", "The token was refused.");
```

| State | Shows |
| --- | --- |
| `running` | A spinner and Cancel, with the message as progress |
| `success` | The message under the row |
| `error` | The message under the row, in error color |

Messages are up to 1000 characters. A finished run's message stays until the next run on that row or the end of the session.

`setupResult` returns `false` once the run is over: canceled, replaced by a new press or timed out. Stop its work when that happens. Kiosk Satellite also tells the plugin:

| Event | Payload |
| --- | --- |
| `settings.cancel` | `run`, `reason`: `canceled` (Cancel or the page closed on the kiosk), `replaced` (pressed again) or `timeout` |
| `settings.confirm` | `run`, `accepted`: the answer to `confirmSetup` |

A run with nothing reported for 60 seconds fails with "The plugin did not answer in time." Report progress during long work to keep it going.

Callbacks still have three seconds. Start the work on a plugin-owned thread and return. `setupResult`, `confirmSetup`, `setSettingChoices` and `updateSettings` can be called from any thread.

## Asking during a run

Some steps need the person's say mid-run, such as trusting a certificate the plugin just received. Ask with `confirmSetup`:

```java
host.confirmSetup(run, "Trust this bridge?", "SHA-256 4F:9A:21:C3\nCheck it against the label on the bridge.", "Trust");
```

The question opens only where the run started: on the kiosk or in the Remote Admin page that pressed the button. The answer arrives as `settings.confirm`. A run asks one question at a time and only while it is going, so a plugin can never open a dialog on its own. An unanswered question fails the run after two minutes.

## Settings that depend on others

`updateSettings(values)` saves only the settings it names. Every other saved value stays, secrets included. Use it to fill in settings that follow from a choice:

```java
host.updateSettings(Collections.singletonMap("lightName", "Kitchen"));
```

`saveSettings(values)` still replaces the whole settings object. A setting it leaves out goes back to its default. Neither calls `configure`, so keep the plugin's own copy in step.

The settings pages save only the setting that changed. A value the plugin saved while the page was open stays.

## Older versions

Older Kiosk Satellite versions ignore `buttons` and `choices`, so the plugin still installs there. The buttons are missing and a choices setting is a plain text field. The new host methods do not exist there, so calling one throws `NoSuchMethodError`. Older versions never send the settings events, so only calls the plugin makes on its own need a guard, such as `updateSettings` from `configure`. Catch `LinkageError` around them, as [SetupDemo.java](../src/me/jxl/kiosk/plugins/hello/SetupDemo.java) does.

A display group in `groups` may name a group that holds only buttons. Older versions reject that manifest, so give such a group a setting too if the plugin should install there.

## Remote API

| Command | Parameters |
| --- | --- |
| `getPluginSetup` | `id`. Returns `runs` by target (`button:ID` or `choices:KEY`) and `choices` by setting key |
| `runPluginSetup` | `id`, `target`: `button` or `choices`, `key`: the button ID or setting key. Returns `run` |
| `cancelPluginSetup` | `id`, `run` |
| `answerPluginSetup` | `id`, `run`, `accepted`: boolean |

A button's `confirm` dialog is drawn by the settings page, so `runPluginSetup` does not ask.

## Hello World demo

The **Setup demo** group talks to a pretend device. **Test** reports progress, then the result. Put `fail` in **Device address** to see an error. **Pair** asks first, then asks you to trust a fingerprint partway through. **Refresh** beside **Device** finds three devices one at a time. Picking one fills in **Device name** with `updateSettings`. See [SetupDemo.java](../src/me/jxl/kiosk/plugins/hello/SetupDemo.java).
