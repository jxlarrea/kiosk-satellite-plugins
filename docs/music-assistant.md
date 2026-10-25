# Music Assistant

SDK 1 plugins reach [Music Assistant](https://www.music-assistant.io/) through the kiosk's own connection. KS signs in with the server address and token from **Settings > Media Player > Music Assistant**, and the plugin never receives the token.

- `host.read` tells a plugin about the kiosk's own Sendspin player and the Music Assistant player that stands for it. See [The kiosk's player](#the-kiosks-player).
- `ma` sends Music Assistant's own API commands. Use it to [control players, queues and the library](#run-commands) and to [follow Music Assistant events](#follow-events).

A plugin can declare either or both. A plugin that only needs to know which player is the kiosk's own needs `host.read` alone.

## The kiosk's player

`getSendspinPlayer` reads the kiosk's own Sendspin player. It needs `host.read`.

```java
host.executeCommand("getSendspinPlayer", Collections.emptyMap(), (ok, data, error) -> {
    if (!ok) return;
    Map<?, ?> player = (Map<?, ?>) data;
    String maPlayerId = (String) player.get("maPlayerId");
    if (!maPlayerId.isEmpty()) showPlayer(maPlayerId);
});
```

| Field | Meaning |
| --- | --- |
| `enabled` | The Sendspin player is turned on in Settings |
| `running` | The player is running. It stops while the kiosk follows another player |
| `connected` | The player is connected to a Sendspin server |
| `playing` | Music is playing on the player `sendspinControl` controls: the followed player when the kiosk follows one, otherwise this one. It stays true for a moment between tracks. Needs Kiosk Satellite 2026.10.41 or newer |
| `clientId` | The player's Sendspin client ID. It stays the same across restarts and updates |
| `playerName` | The name the player registers with, the device name |
| `serverName` | The Sendspin server's name while connected, otherwise empty |
| `maConfigured` | KS has a Music Assistant address and token |
| `maPlayerId` | The ID of the Music Assistant player that stands for this kiosk, otherwise empty. It may be a universal player that wraps the Sendspin client |
| `maServerId` | The Music Assistant server's ID, otherwise empty |
| `maServerName` | The Music Assistant server's name, otherwise empty |
| `maServerVersion` | The Music Assistant server's version, otherwise empty |

KS asks Music Assistant for the player each time the Sendspin player connects. The Music Assistant fields stay empty while the player has never connected, Music Assistant is not set up, or the server does not list the player yet. KS asks again a few seconds later in that case.

Subscribe to `sendspin.player` to hear changes. The payload is the same snapshot plus `time`. It arrives when the player turns on or off, connects or disconnects, starts or stops playing, or Music Assistant names its player.

```java
host.subscribe("sendspin.player");

// Inside onEvent:
if (event.equals("ks.sendspin.player")) {
    String maPlayerId = (String) payload.get("maPlayerId");
}
```

To pause the music for a while, such as during a doorbell call, declare `host.control` too. Remember whether music was playing and only resume it if it was:

```java
private boolean resumeMusic;

void pauseMusic() {
    host.executeCommand("getSendspinPlayer", Collections.emptyMap(), (ok, data, error) -> {
        if (!ok || !Boolean.TRUE.equals(((Map<?, ?>) data).get("playing"))) return;
        resumeMusic = true;
        host.executeCommand("sendspinControl", Collections.singletonMap("command", "pause"), (done, result, failure) -> {});
    });
}

void resumeMusic() {
    if (!resumeMusic) return;
    resumeMusic = false;
    host.executeCommand("sendspinControl", Collections.singletonMap("command", "play"), (done, result, failure) -> {});
}
```

`maPlayerId` is the ID to pass to `players/` commands, and also the queue ID for `player_queues/` commands. Compare `maServerId` with `info` from `maCall` to confirm the kiosk's player belongs to the same server.

## Run commands

Declare `ma` in `capabilities` and pass a command and its arguments to `host.maCall`. The command and arguments are those of Music Assistant's API, which the server documents at `/api-docs` on its web interface.

```java
Map<String, Object> args = new HashMap<>();
args.put("queue_id", maPlayerId);
args.put("media", "library://playlist/12");
args.put("option", "replace");

host.maCall("player_queues/play_media", args, (ok, data, error) -> {
    if (!ok) host.status("Playback failed: " + error, true);
});
```

On success `data` is Music Assistant's result, as detached JSON. KS joins a result Music Assistant sends in parts. On failure `error` reads `code: message` and `data` holds the same as `{code, message}`. The code is Music Assistant's own `error_code` as a string, such as `22` for a token without the permission or `2` for media it could not find, or one of these from KS:

| Code | Meaning |
| --- | --- |
| `not_configured` | KS has no Music Assistant address or token |
| `auth_invalid` | Music Assistant refused KS's token |
| `unreachable` | Music Assistant did not answer |
| `connection_lost` | The connection closed while the command waited |
| `timeout` | No answer within 25 seconds |
| `not_allowed` | The command is outside what plugins may run, listed below |
| `invalid_message` | The arguments are not a JSON object |
| `too_large` | The arguments are larger than 32 KB or the result is larger than 512 KB |

Plugins may run these commands:

- `players/` commands, such as `players/all`, `players/get` and `players/cmd/volume_set`
- `player_queues/` commands, such as `player_queues/play_media`, `player_queues/items` and `player_queues/move_item`
- `music/` commands, such as `music/search`, `music/item_by_uri`, `music/recently_played_items` and `music/favorites/add_item`
- `metadata/` commands, such as `metadata/get_track_lyrics`
- `info` and `providers`

Everything else is refused before it reaches the server, including `auth/` and `config/`, users, tokens, logs and tasks. So are `players/remove`, `players/create_group_player`, `players/remove_group_player` and `metadata/set_default_preferred_language`, which change the server's configuration. The plugin acts as the Music Assistant user that owns KS's token, so Music Assistant's own permissions also apply. A read-only token can browse and read but not play or change anything.

Large lists, such as `music/tracks/library_items`, take `limit` and `offset`. Page through them to stay under 512 KB.

## Follow events

Music Assistant has no subscribe command. It sends every event to every client. `host.maSubscribe(key, filter, callback)` tells KS which of them the plugin wants, under a key of the plugin's choosing, 1 to 64 letters, digits, dots, dashes or underscores. The filter names 1 to 16 events in `events` and may narrow them to one `objectId`, the player or queue ID the event concerns. The callback reports `ok` once the connection is up. Events then arrive through `onEvent("ks.ma.subscription." + key, payload)`.

```java
Map<String, Object> filter = new HashMap<>();
filter.put("events", Arrays.asList("queue_updated", "queue_items_updated"));
filter.put("objectId", maPlayerId);

host.maSubscribe("queue", filter, (ok, data, error) -> {
    if (!ok) host.status("Queue updates failed: " + error, true);
});

// Inside onEvent:
if (event.equals("ks.ma.subscription.queue") && "event".equals(payload.get("type"))) {
    Map<?, ?> queue = (Map<?, ?>) payload.get("data");
}

// When the plugin no longer needs it:
host.maUnsubscribe("queue");
```

Every payload has a `type` and `time`, the KS delivery timestamp:

| `type` | Meaning |
| --- | --- |
| `event` | Music Assistant sent an event. `event` is its name, `objectId` the ID it concerns and `data` its payload as Music Assistant sent it |
| `disconnected` | The connection dropped. KS reconnects with backoff |
| `reconnected` | KS reconnected. Events may have happened in between, so read any state the plugin depends on |
| `overflow` | The plugin fell behind and missed `dropped` events |
| `too_large` | An event larger than 64 KB was left out. `event` and `objectId` say which |

Useful events include `player_updated`, `player_added` and `player_removed` for players, `queue_updated`, `queue_items_updated` and `queue_time_updated` for queues, and `media_item_added`, `media_item_updated`, `media_item_deleted` and `favorite_updated` for the library. `queue_time_updated` arrives every second while a queue plays, so leave it out unless the plugin shows progress.

Events arrive in order and none are merged. KS holds up to 64 for a plugin that is slow to handle them, then drops the newest and reports `overflow`.

Each plugin holds at most 16 subscriptions. Subscribing with a key that is already active fails with `Subscription KEY is already active`, so unsubscribe first to change one. Unknown keys passed to `maUnsubscribe` are ignored. Disabling, uninstalling, updating or stopping a plugin ends all its subscriptions. Subscribe again in `start` or the initial `configure` call. Do not unsubscribe in `stop` because KS has already revoked the host.

`maCall` and `maSubscribe` share the `executeCommand` limits of 8 pending requests and 20 per second. All plugins share one connection, which KS opens on the first request and closes after a minute without requests or subscriptions. A new address or token in Settings starts it over. With the `ma` capability, `getHostApi` lists `ma` in `capabilities` and describes the events in `maSubscriptions` as `{eventPrefix: "ma.subscription.", maxSubscriptions: 16}`.

## Hello World demo

**Music Assistant player** and **Music Assistant volume** in Hello World's readings show the kiosk's Music Assistant player, whether it plays and its volume. The demo reads `getSendspinPlayer`, follows `sendspin.player` and subscribes to `player_updated` for that player. The **Play or pause Music Assistant** action calls `players/cmd/play_pause` on it through `maCall`. Run it from the plugin's actions or map it to a gesture.

See [MusicAssistantDemo.java](../src/me/jxl/kiosk/plugins/hello/MusicAssistantDemo.java) for the complete example.
