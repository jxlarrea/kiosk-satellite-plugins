<h1 align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/ks_plugin_banner_dark.svg" />
    <source media="(prefers-color-scheme: light)" srcset="assets/ks_plugin_banner_light.svg" />
    <img alt="Kiosk Satellite for Home Assistant" src="assets/ks_plugin_banner_default.svg" width="650" />
  </picture>
</h1>

This repository holds the [Kiosk Satellite](https://github.com/jxlarrea/kiosk-satellite) plugin SDK, its documentation and **Hello World**, a working starter template. It also keeps the list behind **Community Plugins**, the plugin directory on [kiosksatellite.com](https://kiosksatellite.com/plugins/) and under **Plugin Manager > Browse plugins** in Kiosk Satellite.

If you installed Hello World, [Hello World starter template](docs/getting-started.md#hello-world-starter-template) walks through what it does.

## Submit your plugin

Listed plugins show up on kiosksatellite.com and in Plugin Manager with their icons, stars and downloads. People install them there with one click.

### Before you submit

Your plugin needs:

- **A public GitHub repository** that holds one plugin and all of its source code, with a license file GitHub recognizes.
- **A stable release built by GitHub Actions.** The Release workflow in this template attaches the manifest, the ZIP and its checksum for you. Kiosk Satellite refuses files you attach by hand, and so does the check.
- **A README** that says what the plugin does, which settings it has and where it sends data, if anywhere. Kiosk Satellite shows it before anyone installs the plugin. It must include the Installation section with the **Install in Kiosk Satellite** button from [Add an install button](#add-an-install-button).
- **A name of your own.** The plugin's name can't be just a trademark or another product's name, such as "Music Assistant". Name what the plugin does with it instead, such as "Music Assistant Control".
- **A unique plugin ID** in `kiosk-satellite-plugin.json`. Keep it once you are listed. A new ID needs a new submission.
- **An icon**, if you want one: a 256 by 256 PNG in `assets/` named by the `icon` field of the manifest. It must be your own artwork, not a company's or product's trademarked logo. Without it the directory shows the generic plugin icon.

Submit from the GitHub account that owns the repository. For a repository that belongs to an organization, make your membership of it public first.

### Steps

1. Run the same check the submission runs from a clone of this repository:

    ```sh
    python3 tools/community_plugins.py check https://github.com/owner/plugin
    ```

    Set `GITHUB_TOKEN` if GitHub limits your requests.
2. Open a [Submit a plugin](https://github.com/jxlarrea/kiosk-satellite-plugins/issues/new?template=submit-plugin.yml) issue and paste your repository URL.
3. Wait for the check. It posts its results on the issue within a minute or two. If something fails, fix it, publish a new release if needed and edit the issue to run the check again.
4. A maintainer reads the code and approves the plugin. The issue then closes and your plugin shows up within 12 hours.

### After you are listed

- **Updates:** publish releases the way you already do. The directory picks up your latest stable release twice a day.
- **Problems:** if a new release fails the check or the repository goes private or archived, the plugin drops out of the directory until a good release fixes it.
- **Removal:** open a [Remove a plugin](https://github.com/jxlarrea/kiosk-satellite-plugins/issues/new?template=remove-plugin.yml) issue.

### Add an install button

Every listed plugin's README needs a one-click install button. It opens the plugin in the reader's Kiosk Satellite, where they review it and choose **Trust and install**. Hello World's button looks like this:

[![Install in Kiosk Satellite](assets/badges/plugin-add.svg)](https://kiosksatellite.com/redirect/plugin/?repository=jxlarrea/kiosk-satellite-plugins)

Copy this Installation section into your README. Replace `owner/plugin` with your repository and `My Plugin` with your plugin's name:

```markdown
## Installation

### Community Plugins (Recommended)

My Plugin is listed in [Community Plugins](https://kiosksatellite.com/plugins/). Use the button below to open it in your Kiosk Satellite and install it.

[![Install in Kiosk Satellite](https://kiosksatellite.com/img/badges/plugin-add.svg)](https://kiosksatellite.com/redirect/plugin/?repository=owner/plugin)

Or search for `My Plugin` in **Settings > Plugin Manager > Browse plugins** in Kiosk Satellite.

### Manual

1. Open **Settings > Plugin Manager** in Kiosk Satellite and turn on **Enable Plugins**.
2. Choose **Add plugin**, paste `https://github.com/owner/plugin` and choose **Preview**.
3. Review the plugin and choose **Trust and install**.
```

## Create a plugin

Plugins are Java code that runs inside Kiosk Satellite. They can draw over the dashboard, add screensavers and voice skins and publish entities to Home Assistant. Start from Hello World, which already shows every feature of the SDK.

Read [Getting started](docs/getting-started.md) to build Hello World, change it into your own plugin and publish your first release.

## License

[Apache-2.0](LICENSE). Kiosk Satellite has its own application license.
