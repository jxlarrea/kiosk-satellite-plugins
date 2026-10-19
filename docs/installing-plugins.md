# Plugin Manager

Plugins add optional features to Kiosk Satellite. SDK 1 supports floating windows, plugin settings, reusable actions, hardware extensions, state observations and transient KS controls. Each plugin lives in its own public GitHub repository. This repository contains the Hello World template, SDK, build tools and plugin documentation.

## Install Hello World

1. Open **Settings > Plugin Manager** on the kiosk or **Plugin Manager** in remote admin and turn on **Enable Plugins**.
2. Choose **Add plugin**, paste its public GitHub repository URL and choose **Preview**.
3. Review the latest stable release's manifest, author, license, capabilities and README. Compatibility errors prevent installation.
4. Choose **Trust and install**. Installation downloads and verifies the reviewed release without running its code.
5. Enable **Hello World** using the switch on its entry row. Return to the dashboard to see its window.

Tap an installed plugin's entry row to open its subpage. Its settings and action shortcut choices appear there. A list setting shows its rows on the subpage. Tap a row to edit it, drag its handle to move it or tap the add row to create one. Remote admin also moves rows with arrow buttons. A setting can show only when another setting holds a certain value. The enable switch sits on the left of the entry row. The check for updates button beside info checks the latest stable GitHub release and lets you review and install an available update. It reports when no update is available. Local ZIP installs use Install from ZIP instead. The info button before delete opens the saved repository README in a modal, including while offline. Local ZIP installs show a message explaining that no repository README is available. Remote admin uses the same layout and supports direct links to plugin subpages.

The master switch explains that plugins add community developed features. A warning about plugin access appears below **Add plugin**. Plugin status refreshes when you open the page.

Drag the window by its title bar. **Say hello** updates its greeting count. Close dismisses the window without disabling the plugin. The **Show window** action opens it again through an assigned gesture, drawer shortcut or Home Assistant button. The Greeting setting changes its message. **Show window when enabled** controls whether it opens automatically.

The window stays inside Kiosk Satellite. It needs no Android permission to draw over other apps. The drawer, media player, screensaver, notifications and lockdown shield keep their existing priority above it. Touches outside the window reach the dashboard.

## Action shortcuts

In **Gestures**, choose **Run a plugin action** and select a command from a running plugin. The mapping keeps the plugin and command IDs. Disabling the plugin pauses its actions and a gesture targeting it reports that it cannot run.

In the plugin subpage, open an action to enable **Show in kiosk drawer** or **Expose to Home Assistant**. Both start off. Drawer shortcuts also appear while locked when the owner allows the kiosk drawer. Home Assistant exposure adds a button to the existing ESPHome device and requires ESPHome with native entities enabled. Settings does not include buttons to run actions directly.

Stopping a plugin removes its active shortcuts and ESPHome buttons. Re-enabling restores the selected placements. An update keeps the choices for commands whose IDs still exist. Removing a command removes its placements. Old gesture mappings remain editable and report a failure until reassigned.

## Enable Plugins

The master **Enable Plugins** switch controls plugin execution and saves its state across app restarts. Turning it off closes plugin windows, revokes host callbacks and stops active sessions. Each plugin keeps its own enabled choice and settings. When the master switch is off, Plugin Manager shows only that switch and hides the remaining settings. Open plugin subpages return to Plugin Manager. Turning the switch back on reveals the controls and resumes the selected plugins.

Plugins start off on kiosks with no installed plugins. Upgrading a kiosk that already has plugins preserves its existing behavior. Newly installed plugins still start individually disabled even when the master switch is on.

## Developer Tools

**Install from ZIP is for developers only.** Users install plugins through **Add plugin** using a public GitHub repository URL. Release assets must be built and published by the repository's GitHub Actions workflow. Manually attached release ZIPs are rejected.

Developers can choose **Install from ZIP** in the **Developer Tools** group to test a local plugin build. On the kiosk, select a file through Android's file picker. In remote admin, select a file from your computer. Review the filename and trust warning, then choose **Trust and install**. The package is validated before installation and starts disabled. Enable it from its entry row when ready.

ZIPs must be at most 4 MB and contain `kiosk-satellite-plugin.json`, `plugin.jar` and `LICENSE`. Local installs do not need a GitHub release or a separate checksum file. They do not have a repository README. Install the replacement ZIP directly. KS stops the old session and restores its enabled state automatically. A normal update does not require an app restart. Compatible settings are retained. Uninstall a repository-installed plugin before switching to a local build. Uninstalling removes its settings.

## Lifecycle and updates

New plugins start disabled. Enabling a plugin runs it immediately and saves that choice for the next app start. Disabling removes its window, revokes its host callbacks and asks it to stop. Removing also deletes its settings.

To update a plugin, use its check for updates button and review the replacement release before choosing **Trust and update**. KS validates the replacement before stopping the active session, then restores the enabled state automatically. Disabled plugins stay disabled. If the master switch is off, the saved enabled choice is retained without running code. A normal update does not require an app restart. If activation fails, KS restores the previous package and settings and resumes it when shutdown was clean. Compatible settings are retained. A replacement with incompatible saved setting types is rejected, leaving the old package installed. Remove and reinstall if you want to discard those settings.

Each callback has a three-second deadline. A failure disables that plugin and records the error in settings. A timed-out callback or failed stop blocks enabling and replacing that plugin until the app restarts. Threads that ignore interruption cannot be forcibly stopped safely. An unfinished automatic plugin startup causes enabled plugins to be disabled on the next launch. This recovery can also trigger if Android kills the process during the startup grace period.

## Trust and package checks

Native plugins run inside Kiosk Satellite with its app identity. They are trusted code and are not sandboxed. A plugin can access app data and Android permissions already granted to Kiosk. Its capability list describes the supported SDK features it uses and is not a security boundary.

The installer accepts ZIPs up to 4 MB and limits expanded content, including nested DEX content. It rejects unexpected paths, duplicate files, unsupported SDK versions and unsupported capabilities. Code is stored in private app storage with read-only DEX containers. Stored digests are checked before loading. A required SHA-256 from the release checksum file checks the downloaded package against the reviewed release. A hash does not establish who wrote a package.

Repository installation discovers the latest stable GitHub release. It reads the attached `kiosk-satellite-plugin.json` and package checksum, then reads `README.md` from the release tag's commit. Drafts and prereleases are excluded. The ZIP contains the same `kiosk-satellite-plugin.json`. Default-branch edits do not change the released documentation. Previews expire after 15 minutes. Installation checks the downloaded bytes and packaged manifest against the preview. Another repository cannot replace an installed plugin with the same ID without uninstalling it first. The reviewed README, release tag and source revision are saved for offline viewing.

KS checks that all release assets were uploaded by GitHub Actions and that the ZIP matches GitHub's asset digest. This checks publication through Actions and does not prove the workflow code is trustworthy. Full artifact attestation verification, private repositories and automatic background updates are not supported. SDK 1 includes native libraries, ESPHome RGB plugin entities and the [documented KS events and transient controls](ks-api.md). Plugin packages and settings are not included in Kiosk configuration export or fleet sync yet.

## Remote API

The existing authenticated command API exposes these commands:

| Command | Parameters |
| --- | --- |
| `getPluginReadings` | `id`: plugin ID. Returns current scalar entity readings without refreshing settings |
| `getPluginCharts` | `id`: plugin ID. Returns current read-only chart snapshots |
| `getPluginStatusTiles` | None. Returns every running plugin's Overview status tiles with the owning plugin's ID and name |
| `getPluginState` | None. Returns the master `enabled` flag and `plugins` list |
| `setPluginsEnabled` | `enabled`: boolean master switch |
| `listPlugins` | None. Returns the installed plugin list. Saved `secret` settings read `__set__` |
| `previewPluginRepository` | `url`: public GitHub repository URL |
| `checkPluginUpdate` | `id` |
| `installPlugin` | Developer testing only. `data`: base64-encoded plugin ZIP up to 4 MB, `trusted`: true |
| `installPluginRepository` | `previewId`: returned by preview, `trusted`: true |
| `enablePlugin` | `id` |
| `disablePlugin` | `id` |
| `removePlugin` | `id` |
| `configurePlugin` | `id`, `values`: complete settings object. With `patch`: true, `values` holds only the settings to change and the rest keep their saved values. A `secret` setting that is omitted or set to `__set__` keeps its saved value. An empty string clears it |
| `getPluginSetup` | `id`. Returns the settings button and choices runs and the listed choices |
| `runPluginSetup` | `id`, `target`: `button` or `choices`, `key`: button ID or setting key |
| `cancelPluginSetup` | `id`, `run` |
| `answerPluginSetup` | `id`, `run`, `accepted`: boolean |
| `getPluginActions` | None. Returns declared actions and their availability |
| `getPluginTriggers` | None. Returns declared gesture triggers and their availability |
| `configurePluginAction` | `id`, `command`, `drawer`: boolean, `homeAssistant`: boolean |
| `runPluginCommand` | `id`, `command`: manifest command ID |

Use `POST /api/commands/<command>` with the existing remote admin authentication. Each plugin's commands are addressed by its stable ID and command ID. They cannot replace core commands. Plugin management is not exposed to dashboard JavaScript.

## Licensing

The SDK and Hello World template use Apache-2.0. The application retains its license with an [additional permission for independent plugins](https://github.com/jxlarrea/kiosk-satellite/blob/main/PLUGIN-EXCEPTION.md). Each plugin must include a license for its own code and respect the licenses of its dependencies.
