# Players

The `player` capability lets a plugin be one of the kiosk's own players. The plugin publishes a player, and it shows under **Settings > Media Player > Player** beside the Sendspin player, AirPlay and Local Media Session, named after the plugin and the player, such as "Hello World: Internet Archive". When the user picks it:

- Its audio plays through the kiosk's own output, the same path as the Sendspin player. The echo canceller hears it, so the wake word works over the music. The media volume and the assistant's duck apply to it.
- Now Playing, the floating player and the ESPHome media player show what it plays, with the buttons it supports.
- Its queue shows in Now Playing and the Music Library, and the user can reorder it there.
- Its library is a Music Library source the user can browse and search.

Only one player is the pick at a time. Picking a plugin player takes the Sendspin player and the AirPlay receiver offline, and picking another player takes the plugin's back.

A receiver, such as an AirPlay or Spotify Connect plugin, needs only the player and its audio. A player that plays from a catalog adds a library and a queue. The Hello World demo plays music from the Internet Archive and uses all of it. See `ArchivePlayerDemo.java`.

## Publish a player

Declare `player` and publish the player from `start`:

```java
host.publishPlayer("radio", "Internet Archive", listener);
```

The key is 1 to 40 lowercase letters, digits and underscores, and the title is up to 80 characters. A plugin can publish two players. Publishing again under the same key with the same listener changes the title. `host.removePlayer(key)` takes it back. A pick that names a player that is not published, because the plugin is disabled, stopped or still starting, stays and shows the player as unavailable. The player is picked again once the plugin publishes it.

## Serve only while picked

The listener hears when the pick arrives and when it leaves:

```java
final class RadioPlayer implements PlayerListener {
    private PluginPlayer player;

    @Override public void onActivate(PluginPlayer player) {
        this.player = player;
        // Start serving: open the audio, listen on the network, advertise the player.
    }

    @Override public void onDeactivate() {
        player = null;
        // Stop playing and serving. KS has already closed the audio output.
    }

    @Override public void onCommand(String command, Map<String, Object> arguments) {
        // "play", "pause", "next", "seek" with {positionMs} and the rest.
    }
}
```

Start nothing before `onActivate`. A receiver that advertises itself while another player is picked shows up on phones as a speaker that plays nowhere, and two receivers of one kind on a device collide on the network.

KS calls the listener on a thread of its own and gives each call three seconds. Hand anything slow, such as network reads, decoding or waiting on a lock another thread holds, to a thread of your own. The Hello World demo runs all of its playback on one control thread and its network reads on another.

## Show what plays

Send the state whenever it changes, such as on a new track, play, pause or seek. KS moves the position on by itself while the player plays, so don't send it on a timer.

```java
Map<String, Object> state = new HashMap<>();
state.put("state", "playing");
state.put("title", "Another Day");
state.put("artist", "No-Source");
state.put("album", "Another Day, Another Way");
state.put("artworkUrl", "https://archive.org/services/img/NS050");
state.put("durationMs", 254000L);
state.put("positionMs", 0L);
state.put("actions", Arrays.asList("play", "pause", "next", "previous", "seek", "shuffle", "repeat"));
player.update(state);
```

| Key | Meaning |
| --- | --- |
| `state` | `idle`, `buffering`, `playing` or `paused`. Idle shows nothing |
| `title`, `artist`, `album` | Up to 512 characters each |
| `artworkUrl` | An `http` or `https` image address |
| `artwork` | The image itself as `byte[]`, JPEG or PNG, at most 1 MiB, for a cover that has no address. It wins over `artworkUrl` |
| `durationMs` | The track's length. Leave it out for a stream with no end. Without it the seek bar does not show |
| `positionMs` | Where the track is right now |
| `shuffle` | `true` or `false` |
| `repeat` | `off`, `one` or `all` |
| `actions` | The commands the player takes: `play`, `pause`, `stop`, `next`, `previous`, `seek`, `shuffle` and `repeat`. Only these show as buttons and only these reach `onCommand`. Leave it empty for a player that only shows what plays, such as a receiver whose sender offers no remote control |

Volume is not part of the state. KS applies its own media volume and the assistant's duck to the audio output, so the volume slider and the volume keys work for every plugin player. A plugin that receives its own volume, such as a sender's volume, applies it to its PCM before writing it.

## Play audio

Open the output once the player is the pick, and write 16-bit PCM to it from a thread of your own:

```java
AudioOutput out = player.openAudio(44100, 2, PluginPlayer.ENCODING_PCM_16);
while (playing) {
    ByteBuffer pcm = decoder.next();
    while (pcm.hasRemaining()) {
        if (out.write(pcm, pcm.remaining() / 4, 500) < 0) return; // closed
    }
}
```

The sample rate is 8000 to 192000 and the channels 1 or 2. `write` takes whole frames from the buffer's position and moves the position past them. It blocks while the output is full, at most the timeout. It returns -1 once the output is closed, which happens when the pick moves on. Opening the output again replaces the one before. Keep one output for as long as the format stays the same, so tracks follow each other without a new stream.

`flush()` drops what is queued and starts a new count from zero. Use it to pause, seek or skip. To pause, flush and stop writing. Writing again starts the output again.

### Keep time

A plugin that plays in sync with other speakers, such as an AirPlay 2 receiver, needs to know when written frames are heard. `getTimestamp` reports it the way AAudio and ALSA do:

```java
long[] ts = new long[2];
if (out.getTimestamp(ts)) {
    long heardNow = ts[0] + (System.nanoTime() - ts[1]) * rate / 1_000_000_000L;
    long queued = out.framesWritten() - heardNow; // written but not yet heard
}
```

`ts[0]` is a frame position and `ts[1]` the `System.nanoTime()` at which that frame was heard. Both count from the last flush. The timestamp comes from the same clock the echo canceller reads, so a sync engine and the echo canceller agree on what plays when. It returns false until the first frames are heard.

A plugin whose audio comes from a helper process, such as a native receiver, reads the PCM from the helper and writes it here. To keep the helper's own sync working, pass the timestamp to the helper every 100 ms or so. The helper then works out its delay from its own count of frames written, without a round trip for each call.

## A queue

Implement `PlayerQueue` on the listener to show the queue in Now Playing and the Music Library and let the user edit it there:

```java
@Override public void queue(PluginHost.CommandCallback callback) {
    Map<String, Object> queue = new HashMap<>();
    queue.put("items", items);  // maps of id, title, artist, artworkUrl and durationMs
    queue.put("current", index); // the playing one, or -1
    callback.onResult(true, queue, null);
}
@Override public void playItem(String id) { }
@Override public void moveItem(String id, int toIndex) { }
@Override public void removeItem(String id) { }
@Override public void clear() { }
```

Give each queue entry an id of its own, so the same track queued twice can still be moved or removed on its own. `moveItem` names the index the item ends up at. Call `player.queueChanged()` whenever the queue changes, and KS asks for it again while it is on screen. A queue holds at most 500 items.

## A library

Implement `PlayerLibrary` on the listener to add a Music Library source under the player's name. The library lists it whenever the plugin runs, the same as Music Assistant or Sonos. Its music plays only on the plugin's own player, so while another player is picked the library's speaker pill asks the user to pick this one first.

```java
@Override public boolean hasDiscover() { return true; }
@Override public boolean hasSearch() { return true; }
@Override public void discover(PluginHost.CommandCallback callback) { }  // a list of shelves
@Override public void browse(String folderId, int offset, PluginHost.CommandCallback callback) { }  // {items, more}
@Override public void search(String query, PluginHost.CommandCallback callback) { }  // a list of shelves
@Override public void open(String itemId, PluginHost.CommandCallback callback) { }  // {item, items}
@Override public void play(String itemId, String mode, String withinId, PluginHost.CommandCallback callback) { }
```

KS reads `hasDiscover` and `hasSearch` when the player is published. With Discover, the library opens on its shelves. Otherwise it opens on the root of `browse`.

An item is a map:

| Key | Meaning |
| --- | --- |
| `id` | Required, up to 512 characters. The plugin's own key for it |
| `kind` | `artist`, `album`, `track`, `playlist`, `radio`, `podcast`, `episode`, `audiobook`, `genre`, `folder` or `other`. It picks the icon and the shape of the cover |
| `title`, `subtitle` | The subtitle is the artist, the owner or whatever goes under the title |
| `artworkUrl` | An `http` or `https` image address |
| `durationMs` | A track's length |
| `playable` | Defaults to true |
| `browsable` | Defaults to false. A browsable folder opens with `browse`, and a browsable album or playlist opens with `open` |

A shelf is a map of `title` and `items`. `browse` answers a page as `{items, more}`, with `more` true when another page follows from `offset` plus the items sent. KS asks for pages until `more` is false or it has 1000 items.

`play` names the mode:

| Mode | Meaning |
| --- | --- |
| `replace` | Clear the queue and play |
| `now` | Play now and keep the rest of the queue |
| `next` | Play after the current track |
| `add` | Add to the end of the queue |

`withinId` is the album or playlist a track was tapped in, or null, so the rest of it can follow a `replace`.

Answer every call through the callback, with the data or with `ok` false and an error, which the library shows. Each call has 20 seconds. Answers are checked and cut to size: KS keeps the keys above, up to 200 items in a page, 20 shelves of 100 items and 500 tracks in an album.

## What the user sees

- **Settings > Media Player > Player** lists the plugin's players under This device, on the kiosk and in Remote Admin. Picking one shows the same notice as AirPlay: the Sendspin player and the AirPlay receiver go offline while it is the pick.
- **Now Playing** and the floating player show the state, with the buttons from `actions` and the device's volume. The chip names the player.
- **Music Library** lists the plugin's library as a source, with an extension icon. **Settings > Media Player > Music Library > Source** lists it too.
- The `mediaPlayerSet` action picks a plugin player by its name or its id with the device source, so a gesture, a voice request or Home Assistant can switch to it.
