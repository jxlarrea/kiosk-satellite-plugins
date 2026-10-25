# Home Assistant

SDK 1 plugins reach Home Assistant in two ways. KS manages authentication for both with its configured Home Assistant URL and token, and the plugin never receives the credentials.

- `host.read` reads entity states and follows their changes. It cannot change anything in Home Assistant.
- `ha` sends Home Assistant's own [WebSocket API](https://developers.home-assistant.io/docs/api/websocket) messages. Use it to [call services and run other commands](#call-services-and-other-commands) and to [subscribe to triggers, events and templates](#subscribe-to-triggers-events-and-templates).

A plugin can declare either or both.

## Choose an entity in Settings

Declare `host.read` in `capabilities` and add an `entity` setting:

```json
{
  "key": "roomEntity",
  "type": "entity",
  "title": "Room temperature",
  "default": "",
  "group": "Home Assistant",
  "description": "Choose the entity to display."
}
```

Both settings interfaces use KS's existing searchable entity picker. The value is the selected entity ID, such as `sensor.living_room_temperature`. An empty string means no selection. Clear removes the selection. Changes save automatically and arrive in `configure` like other plugin settings.

The picker leaves out entities that Home Assistant hides. Add `"includeHidden": true` to list them too, marked with the hidden icon. Use it for entities people hide from generated dashboards but still want to control, such as a light group. It works the same on `entity` fields in a `list` setting. Older Kiosk Satellite versions ignore it.

Entity IDs must match `domain.object_id`, with lowercase letters, digits or underscores and at most 255 characters. Wildcards and URL paths are rejected. Entity settings are a convenience for choosing IDs, not an access restriction. `host.read` allows reading any entity available to KS's configured HA account. Plugins run inside KS and are trusted code.

## Read the current state

```java
host.executeCommand("getHaEntityState",
    Collections.singletonMap("entityId", "sensor.living_room_temperature"),
    (ok, data, error) -> {
        if (!ok) {
            host.status(error, true);
            return;
        }
        Map<?, ?> snapshot = (Map<?, ?>) data;
        host.log("Entity status: " + snapshot.get("status"));
    });
```

| Field | Meaning |
| --- | --- |
| `entityId` | The requested entity ID |
| `status` | Availability status listed below |
| `state` | HA's state string, including literal `unknown` or `unavailable`. Null when no current state is available |
| `attributes` | Detached JSON object of the entity's attributes, including nested values. Empty when no current state is available |
| `lastChanged` | HA's last state-change timestamp in UTC, or null |
| `lastUpdated` | HA's last-update timestamp in UTC, or null |

Status is one of `available`, `unknown`, `unavailable`, `missing`, `not_configured`, `connecting`, `disconnected` or `too_large`. A valid read can return `ok=true` with one of these statuses. `ok=false` means the SDK request itself failed, such as invalid arguments or an expired plugin session.

`missing` means HA did not return that entity for its authenticated account. `disconnected` also covers authentication failures. Old state and attributes are cleared while disconnected. Do not treat a missing or unavailable value as zero, off or a successful measurement.

Attributes contain the data supplied by HA integrations. They may include personal information or URLs with their own credentials. Treat them as untrusted data and avoid logging complete attribute objects. KS omits the HA state context and does not add its own access token to the payload.

## Subscribe to changes

```java
host.subscribe("ha.entity.sensor.living_room_temperature");

// Inside onEvent:
if (event.equals("ks.ha.entity.sensor.living_room_temperature")) {
    String status = (String) payload.get("status");
    Object state = payload.get("state");
    // Update the plugin's display or hardware using the confirmed state.
}

// When configure switches to a different entity:
host.unsubscribe("ha.entity.sensor.living_room_temperature");
```

A subscription delivers the initial snapshot and subsequent changes, including attribute-only updates and removal. The payload uses the read format above plus `time`, the KS event delivery timestamp. KS merges HA's compressed updates, including deleted attributes, before delivering each snapshot.

KS opens a shared, filtered WebSocket for the entities requested by active plugins using the existing HA connection configuration. It does not subscribe to every entity or reuse the dashboard's filtered event stream. It reconnects with backoff, refreshes the initial states and restarts when the HA connection settings change. `connecting` and `disconnected` events distinguish transport availability from an entity's `unknown` or `unavailable` state.

Each plugin session can subscribe to at most 16 distinct entities. Duplicate subscriptions are harmless. Subscriptions use the existing serialized plugin callback worker and are coalesced per entity over 100 ms. Slow plugins receive the latest pending state for each entity. This is a current-state feed, not an event history or exact change counter.

Unsubscribing discards pending events for that entity. Disabling, uninstalling, updating or stopping a plugin revokes all its subscriptions. Subscribe again in `start` or the initial `configure` call. Do not unsubscribe in `stop` because KS has already revoked the host. If a user changes the selected entity, unsubscribe from the old ID and ignore callbacks for it.

Snapshots are limited to 30,000 UTF-8 JSON bytes to fit the existing SDK response and event limits. An oversized entity returns `too_large` with cleared values. A live entity that exceeds the limit remains `too_large` until a fresh full state is received, such as after resubscribing. Regular reads obtain a fresh full state. Existing callback, command-rate and session limits still apply.

`getHostApi` lists `getHaEntityState` in `commands` and describes dynamic entity events in `entitySubscriptions` as `{eventPrefix: "ha.entity.", maxEntities: 16}`. These events are not individual entries in the fixed `events` list. All of this remains SDK 1.

## Call services and other commands

Declare `ha` in `capabilities` and pass a message to `host.haCall`. The message is what Home Assistant's WebSocket API expects, without the `id`. KS numbers each message itself.

```java
Map<String, Object> message = new HashMap<>();
message.put("type", "call_service");
message.put("domain", "light");
message.put("service", "turn_on");
message.put("service_data", Collections.singletonMap("brightness_pct", 40));
message.put("target", Collections.singletonMap("entity_id", "light.kitchen"));

host.haCall(message, (ok, data, error) -> {
    if (!ok) host.status("Light failed: " + error, true);
});
```

On success `data` is the message's `result` from Home Assistant, as detached JSON. A `call_service` result holds the call's `context`, and `response` too when the message sets `return_response`. On failure `error` reads `code: message`, and `data` holds the same as `{code, message}`. The code is Home Assistant's own, such as `service_not_found` or `invalid_format`, or one of these from KS:

| Code | Meaning |
| --- | --- |
| `not_configured` | KS has no Home Assistant URL or token |
| `auth_invalid` | Home Assistant refused KS's token |
| `unreachable` | Home Assistant did not answer |
| `connection_lost` | The connection closed while the command waited |
| `timeout` | No answer within 25 seconds |
| `not_allowed` | A credential command or one KS handles itself, listed below |
| `invalid_message` | The message has no `type`, or it has an `id` |
| `too_large` | The message or the result is larger than 32 KB |

Any command works, `call_service`, `get_config`, `history/history_during_period` or `config/entity_registry/list_for_display` among them. The plugin acts as the Home Assistant user that owns KS's token. Credential and user management commands are refused: `auth/long_lived_access_token`, the other `auth/` commands except `auth/current_user` and `auth/sign_path`, and the `config/auth` user and password commands. So are `ping`, `supported_features` and `unsubscribe_events`, which KS sends itself.

A result must fit in 32 KB. `get_states` on a typical install does not, so read the entities the plugin needs through `host.read` or a filtered command instead. A service call that runs a script directly waits for the script to finish, so start long scripts with `script.turn_on` instead.

Commands that stream events, such as `render_template`, belong in `haSubscribe`. When `haCall` sends one, the plugin gets only Home Assistant's acknowledgment and KS stops the stream.

## Subscribe to triggers, events and templates

`host.haSubscribe(key, message, callback)` starts a subscription under a key of the plugin's choosing, 1 to 64 letters, digits, dots, dashes or underscores. The callback says whether Home Assistant accepted it, with the same `error` and `data` as `haCall`. Events then arrive through `onEvent("ks.ha.subscription." + key, payload)`.

```java
Map<String, Object> trigger = new HashMap<>();
trigger.put("platform", "state");
trigger.put("entity_id", "binary_sensor.front_door");
trigger.put("to", "on");
Map<String, Object> message = new HashMap<>();
message.put("type", "subscribe_trigger");
message.put("trigger", trigger);

host.haSubscribe("door", message, (ok, data, error) -> {
    if (!ok) host.status("Door trigger failed: " + error, true);
});

// Inside onEvent:
if (event.equals("ks.ha.subscription.door") && "event".equals(payload.get("type"))) {
    // The door opened. payload.get("event") is Home Assistant's event.
}

// When the plugin no longer needs it:
host.haUnsubscribe("door");
```

Every payload has a `type` and `time`, the KS delivery timestamp:

| `type` | Meaning |
| --- | --- |
| `event` | Home Assistant sent an event. `event` holds it as Home Assistant sent it, such as `{variables: {trigger: {...}}}` for `subscribe_trigger` |
| `disconnected` | The connection dropped. KS reconnects with backoff |
| `resubscribed` | KS reconnected and subscribed again. Events may have happened in between, so read any state the plugin depends on |
| `ended` | Home Assistant refused the subscription after a reconnect, with `error` as `{code, message}`. The key is free again |
| `overflow` | The plugin fell behind and missed `dropped` events |
| `too_large` | An event larger than 30 KB was left out |

Events arrive in order and none are merged, unlike `ha.entity.*` states. KS holds up to 64 for a plugin that is slow to handle them, then drops the newest and reports `overflow`.

Use a subscription for anything Home Assistant streams: `subscribe_trigger`, `subscribe_events` with an `event_type`, `render_template`, `todo/item/subscribe` and the like. Prefer a narrow trigger or event type over all events. To follow entity states, `host.read` and `ha.entity.*` already do that with less traffic.

Each plugin holds at most 16 subscriptions. Subscribing with a key that is already active fails with `Subscription KEY is already active`, so unsubscribe first to change one. Unknown keys passed to `haUnsubscribe` are ignored. Disabling, uninstalling, updating or stopping a plugin ends all its subscriptions. Subscribe again in `start` or the initial `configure` call. Do not unsubscribe in `stop` because KS has already revoked the host.

`haCall` and `haSubscribe` share the `executeCommand` limits of 8 pending requests and 20 per second. All plugins share one connection, which KS opens on the first request and closes after a minute without requests or subscriptions. With the `ha` capability, `getHostApi` lists `ha` in `capabilities` and describes the events in `haSubscriptions` as `{eventPrefix: "ha.subscription.", maxSubscriptions: 16}`.

## Hello World demo

Open **Home Assistant demo > Home Assistant entity** in the Hello World plugin and select an entity. The plugin publishes live state and detail readings in its subpage. Change that entity in HA to see updates without polling. Clear the selection to remove those demo readings. The greeting, chart, Shizuku and DVD screensaver demos continue to work independently.

The demo also subscribes to a state trigger on the entity, and **Home Assistant trigger** counts the changes it reports. The **Toggle Home Assistant entity** action calls `homeassistant.toggle` on it through `haCall`. Run it from the plugin's actions or map it to a gesture.

See [HomeAssistantDemo.java](../src/me/jxl/kiosk/plugins/hello/HomeAssistantDemo.java) for the complete example.
