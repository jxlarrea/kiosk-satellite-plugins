# Voice skins

A plugin can draw the Voice Satellite overlay itself. Declare `voice.skin` and the plugin appears under its own name in **Voice Satellite > Appearance > Assistant 1 skin** and **Assistant 2 skin**, after the built-in skins. While the user has it picked for the assistant answering and the plugin runs, KS draws none of its own overlay for a turn: no backdrop, art, activity bar or text. The plugin draws the turn in its own [native overlays](overlays.md) instead.

A skin draws two looks, one for each **Overlay mode** the user can pick: full screen and docked. Both follow the other [Appearance settings](#appearance-settings): Theme, Background, Text size and Reactive activity bar. KS draws neither under a plugin skin, so a skin that leaves one out shows nothing in that mode.

KS still shows rich results such as images, weather and videos over the plugin's full screen look, along with error toasts and timer pills. Double tap still dismisses the turn. If the plugin is disabled, stops or is uninstalled, KS draws the Kiosk Satellite skin until the plugin runs again. The user's pick stays saved.

## Capabilities

| Capability | What it gives the plugin |
| --- | --- |
| `voice.skin` | The plugin's entry in the skin lists. Requires `overlay` and `host.read` |
| `voice.text` | What the user said and the assistant answered, through `getVoiceText` and `voice.text`. Requires `host.read`. Optional for a skin |

A skin without `voice.text` can still draw the turn from its state and the voice level. `voice.text` also works without `voice.skin`, for a plugin that logs or shows the conversation somewhere else.

```json
"capabilities": ["overlay", "host.read", "voice.skin", "voice.text"]
```

## When to draw

Draw only while `getVoiceState` returns `skinSelected: true`, or the latest `voice.skin` event says `selected: true`. Otherwise the user picked another skin and KS draws the turn. While a turn shows, `selected` follows the assistant answering: **Assistant 2 skin** for a turn wake word 2 starts and **Assistant 1 skin** for every other turn. While nothing shows, it is true if either setting picks the plugin. KS sends `voice.skin` before the `voice.overlay` event of the same turn, so check it before you draw. Then draw the look `overlay` names, from `getVoiceState` or the latest `voice.overlay` event:

| `overlay` | What the skin draws |
| --- | --- |
| `full` | The full screen look. It covers the kiosk, like the built-in skins |
| `docked` | The docked look: a small bubble that leaves the dashboard in view and usable. Keep it clear of the dashboard's controls and leave touches to them |
| `hidden` | Nothing. Remove the overlay |

`overlay` can change from `full` to `docked` and back while a turn shows when the user changes **Overlay mode**. Replace the overlay with the other look when it does.

| Event | Payload |
| --- | --- |
| `voice.skin` | `selected`: true while the skin setting of the assistant on screen picks this plugin |
| `voice.appearance` | `theme`, `background`, `textScale` and `reactive`. See [Appearance settings](#appearance-settings) |
| `voice.overlay` | `overlay`: `hidden`, `full` or `docked` |
| `voice.level` | `level`: 0 to 1 |
| `voice.text` | `command`, `answer`, `tools`, `streaming` and `announcement`. Requires `voice.text` |

Follow `voice.overlay` rather than `voice.state` to decide when to show. The overlay also shows for **Preview** and while an answer stays on screen after it is spoken. `voice.state` reads `idle` at those times.

Show the overlay with `onTop(true)` so it draws over the screensaver, camera views and the black screen-off cover, and with `touchable(false)` unless it has controls. Touches that pass through reach KS, which dismisses the turn on a double tap.

```java
host.showOverlay("skin",
    OverlaySpec.fullScreen().onTop(true).touchable(false).closeOnBack(false)
        .enter(OverlaySpec.FADE).exit(OverlaySpec.FADE).transitionDuration(300),
    new SkinFactory());
```

For the docked look, anchor a wrapped overlay to the bottom of the screen:

```java
host.showOverlay("skin",
    OverlaySpec.at(OverlaySpec.BOTTOM).inset(24).onTop(true).touchable(false).closeOnBack(false),
    new DockedFactory());
```

Read `getVoiceState` once after subscribing and let events win over an older read, the same as for [Voice Satellite state](ks-api.md#voice-satellite-state).

## Appearance settings

A skin follows the settings under **Voice Satellite > Appearance** the way the built-in skins do. Read them with `getVoiceAppearance` and follow `voice.appearance`, which fires when one changes. Both need only `host.read`.

| Field | Setting | What the skin does |
| --- | --- | --- |
| `theme` | Theme | `auto`, `light` or `dark`. With `auto`, use `KsTheme.isDark()` from the overlay's theme. With `light` or `dark`, draw that palette whatever the KS theme is |
| `background` | Background | The full screen backdrop's opacity from 0 to 1. `null` means Skin default: use the skin's own. The docked look has no backdrop |
| `textScale` | Text size | A factor from 0.5 to 2. Multiply every text size in both looks by it |
| `reactive` | Reactive activity bar | While false, nothing moves with the voice. `voice.level` stays at 0 then |

```java
host.subscribe("voice.appearance");
host.executeCommand("getVoiceAppearance", Collections.emptyMap(), (ok, data, error) -> {
    if (ok) apply((Map<?, ?>) data);
});

// In KioskPlugin.onEvent:
if (event.equals("ks.voice.appearance")) apply(payload);
```

## Thumbnail

Name a picture of the skin with `voiceSkinThumbnail` in the manifest. KS shows it in the skin picker on the kiosk and in the remote admin, where the built-in skins show theirs. Without one, the plugin shows a generic plugin icon.

```json
"voiceSkinThumbnail": "voice-skin.webp"
```

The path is relative to the plugin's `assets/` folder. Use a PNG, JPEG or WebP image of at most 256 KB, 16:10 and about 640 by 400 pixels. The picker crops it to fill its tile. A screenshot of the full screen look answering a question reads best next to the built-in skins. KS refuses to install a package whose manifest names a missing or larger image, and a manifest that names one without declaring `voice.skin`.

## Voice level

`voice.level` is the level the built-in skins' reactive bar follows: the user's voice while listening and the answer while it plays. It is 0 while the assistant thinks, while the wake chime plays, when no turn shows and while **Reactive activity bar** is off. It needs only `host.read`. No audio is delivered.

KS sends it about 30 times a second while it changes, and once more with 0 when the turn ends. Ease toward the latest value on each frame instead of jumping to it.

## Conversation text

With `voice.text`, `getVoiceText` and the `voice.text` event return the turn's words as the built-in skins would show them.

| Field | Meaning |
| --- | --- |
| `command` | What the user said. Empty while **Show what you said** is off |
| `answer` | The assistant's answer or an announcement's text. Empty while **Show the answer** is off. Sentiment tags such as `[happy]` are left out while **Hide sentiment tags** is on |
| `tools` | A list with one line per action the assistant took. Empty while **Show tool use** is off |
| `streaming` | True while the answer is still arriving |
| `announcement` | True while an announcement shows instead of a turn |

The event fires when any field changes, including when the user changes one of those Conversation settings. A streamed answer arrives in steps. Each event carries the whole answer so far, not just the new words. Fields are empty when no turn shows. The command and answer are cut at 16,000 characters and tools at 20 lines.

```java
host.subscribe("voice.text");

// In KioskPlugin.onEvent:
if (event.equals("ks.voice.text")) {
    String command = (String) payload.get("command");
    String answer = (String) payload.get("answer");
    // Hand the words to the overlay view on its own thread.
}
```

## Example

Hello World declares `voice.skin` and `voice.text` and ships a thumbnail. Pick **Hello World** in **Voice Satellite > Appearance > Assistant 1 skin** and press **Preview**, or say the wake word. A ring swells with the voice, an arc turns while the assistant thinks and the conversation shows under it. With **Overlay mode** set to Docked, the same ring and conversation sit in a card at the bottom of the screen. Change Theme, Background, Text size or Reactive activity bar to see both looks follow them. See [VoiceSkinDemo.java](../src/me/jxl/kiosk/plugins/hello/VoiceSkinDemo.java).

KS versions without voice skins refuse to install a plugin that declares `voice.skin` or `voice.text`.
