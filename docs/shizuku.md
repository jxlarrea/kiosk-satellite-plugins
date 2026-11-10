# Shizuku access

SDK 1 plugins can use KS's Shizuku connection to run commands with the backend's shell or root identity. Declare `shizuku` in `capabilities`. Existing plugins need no changes. Hello World declares the capability and includes an opt-in Shizuku demo. Declaring the capability does not request permission or run commands.

KS includes the Shizuku API and provider. Do not bundle another copy, modify the app manifest or use Shizuku's deprecated `newProcess` method. KS uses a dedicated UserService for each plugin session. Keep `apiVersion: 1` and copy the current SDK interfaces when adding these methods to an existing repository.

## Connect

Install and start [Shizuku](https://shizuku.rikka.app/guide/setup/) on the kiosk. Shizuku server version 13 or later is required. Open **Settings > Device > Shizuku** or the plugin subpage in **Plugin Manager** and tap **Shizuku access** to request permission. Approve Shizuku's prompt on the kiosk. Remote Admin provides the same status and can request the on-device prompt. If access was denied permanently, allow Kiosk Satellite from the Shizuku app.

A plugin cannot trigger permission prompts through the SDK. Its `start()` should handle unavailable or denied access without throwing. The permission belongs to **Kiosk Satellite**, not to a separate plugin package. All installed plugins execute inside KS and the capability declaration is not a security sandbox. Grant Shizuku access only when you trust your installed plugins.

Shizuku started through ADB normally provides shell access, not root. Shell may read diagnostics but cannot automatically write protected LED sysfs nodes or read another app's private files. Check the backend UID and command result. KS does not start Shizuku, obtain root, change Android permissions automatically or substitute `su` when access is unavailable. The existing `native`, `host.read` and `host.control` APIs keep their behavior.

The Device subpage also offers a connection test and explicit permission actions for KS. It works with Plugin Manager disabled. These actions only run when tapped and do not change the plugin SDK's behavior. See the [Device Shizuku guide](https://github.com/jxlarrea/kiosk-satellite/blob/main/docs/shizuku.md) for the supported actions and general Remote API commands.

## SDK methods

```java
Map<String, Object> state = host.shizukuState();
if (Boolean.TRUE.equals(state.get("granted"))) {
    host.executeShizuku(new String[] {"/system/bin/id"}, 2000, (ok, data, error) -> {
        if (!ok) {
            host.status(error, true);
            return;
        }
        Map<?, ?> result = (Map<?, ?>) data;
        int exitCode = ((Number) result.get("exitCode")).intValue();
        boolean timedOut = Boolean.TRUE.equals(result.get("timedOut"));
        String output = (String) result.get("stdout");
        // Check exitCode, timedOut and truncated before using the output.
    });
}
```

`shizukuState()` returns a detached map and never prompts or starts a helper:

| Field | Meaning |
| --- | --- |
| `status` | `unavailable`, `unsupported`, `permission_required`, `denied` or `ready` |
| `available` | The Shizuku binder is reachable |
| `granted` | KS has access to a supported Shizuku backend |
| `uid` | Backend UID when supported and connected. Usually `2000` for shell or `0` for root |
| `version` | Connected Shizuku server API version |

Plugins declaring `shizuku` receive `onEvent("shizuku.state", state)` when the binder connects, disconnects or a permission request completes. Read the initial state in `start()`. No `host.read` capability or subscription is needed. Treat this event as a reason to recheck readiness, not as a guarantee that the next command will succeed.

`executeShizuku(String[] command, int timeoutMs, CommandCallback callback)` is asynchronous. The callback runs on the plugin's serialized callback executor and follows its existing three-second callback deadline. Do not block waiting for it inside `start`, `configure`, `execute` or `onEvent`. `stop()` is the exception, see [Bounds and lifecycle](#bounds-and-lifecycle).

| Result field | Meaning |
| --- | --- |
| `exitCode` | The command's exit code or `-1` when it timed out |
| `stdout` | UTF-8 standard output |
| `stderr` | UTF-8 standard error |
| `timedOut` | The process exceeded its requested execution deadline |
| `truncated` | Either output stream exceeded its capture limit |

`ok=true` means KS received a command result. A nonzero exit code or `timedOut=true` is still an unsuccessful command. `ok=false` reports unavailable permission, connection failure or a helper deadline. Invalid arguments, a stopped session, missing capability or a busy/rate-limited session throw immediately. Handle both synchronous failures and callback errors.

## Bounds and lifecycle

- Pass an absolute executable path and separate arguments. KS does not concatenate or interpolate a shell command. Use `/system/bin/sh -c` explicitly only when you need shell syntax and handle untrusted values safely.
- Supply 1 to 32 arguments, at most 4096 characters per argument and 16384 characters total. Null characters are rejected.
- Execution timeouts range from 100 to 30000 milliseconds. Helper connection has a separate five-second deadline. KS also enforces an overall watchdog that includes connection overhead.
- One command may be pending per plugin, with at least 250 milliseconds between requests. Both output streams are drained and each retains at most 32768 bytes. Stdin is closed immediately.
- Shizuku stays available while `stop()` runs, so a plugin can undo what it set up, such as iptables rules. Every other host call is already revoked by then. During `stop()` the callback runs on KS's Shizuku thread instead of the plugin's executor, so `stop()` can wait for it within its three-second deadline. Once `stop()` returns, KS takes no new commands. A command already sent still runs to its timeout, its callback is discarded and then the helper stops. This applies to disabling, uninstalling, updating or stopping the plugin and to a master plugin shutdown. Other plugin sessions keep their own helpers.
- KS discards a timed-out or disconnected helper. The next request can reconnect when Shizuku is available and permission remains granted. Permission changes may cause Android or Shizuku to restart KS.
- Helpers are not daemons. Shizuku stops them when the KS process dies. Stopping a helper kills its process group and ordinary child commands. Do not launch detached daemons or commands that create a new session or process group. This API is for bounded commands, not persistent root services, streaming processes or interactive shells.

Running a command does not automatically update settings or Home Assistant entities. Publish the observed result through the regular SDK methods. Granting Shizuku access can permit changes beyond the read-only KS APIs. Plugin authors are responsible for the commands they choose.

## Hello World demo

Hello World's **Shizuku demo** group contains **Enable Shizuku demo** and a **Diagnostic** selector. The toggle defaults to off. After KS has Shizuku access, enable it to read one of these fixed commands every 10 seconds:

| Diagnostic | Command |
| --- | --- |
| Process identity | `/system/bin/id` |
| Android version | `/system/bin/getprop ro.build.version.release` |
| Kernel version | `/system/bin/uname -r` |

**Shizuku demo status** and **Shizuku result** appear under Readings and as text sensors when exposed to Home Assistant. The chart's simulated values remain separate from these real device readings. The demo keeps working when the chart is hidden.

[ShizukuDemo.java](../src/me/jxl/kiosk/plugins/hello/ShizukuDemo.java) handles unavailable access, timeouts, nonzero exits, truncated output and synchronous request failures. It allows one pending request, clears stale readings and discards callbacks after a selection change or stop. The existing sampler checks for work without blocking KS callbacks. Disable the demo to stop new commands or disable the plugin to stop its helper.

## Try the separate example

The [Shizuku example](../examples/shizuku) reads `/system/bin/id` and publishes the identity as a text sensor in **Readings**. It changes no device settings and does not run any command before KS has permission.

```bash
python3 tools/build.py examples/shizuku --android-platform 35
```

Install its ZIP through **Developer Tools > Install from ZIP** for local testing. Distributed plugins still install from GitHub Actions-built release assets as described in the [installation guide](installing-plugins.md).

## Remote Admin commands

`getPluginShizukuState` returns the current connection and permission state. `requestPluginShizukuPermission` takes `{"id":"plugin-id"}` and requires Plugin Manager to be enabled and that installed plugin to declare `shizuku`. It requests the Shizuku permission prompt on the kiosk. Neither command executes shell commands. Fleet credentials cannot call either command and Shizuku permission is not synchronized between kiosks.

The integration follows the [official Shizuku API guide](https://github.com/RikkaApps/Shizuku-API), including its UserService lifecycle and shell/root distinction.
