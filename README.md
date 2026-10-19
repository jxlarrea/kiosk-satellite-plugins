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

Listed plugins show up on kiosksatellite.com and in Plugin Manager with their stars, downloads and active installs. People install them there with one click.

### Before you submit

Your plugin needs:

- **A public GitHub repository** that holds one plugin and all of its source code, with a license file GitHub recognizes.
- **A stable release built by GitHub Actions.** The Release workflow in this template attaches the manifest, the ZIP and its checksum for you. Kiosk Satellite refuses files you attach by hand, and so does the check.
- **A README** that says what the plugin does, which settings it has and where it sends data, if anywhere. Kiosk Satellite shows it before anyone installs the plugin.
- **A unique plugin ID** in `kiosk-satellite-plugin.json`. Keep it once you are listed. A new ID needs a new submission.
- **An icon**, if you want one: a 256 by 256 PNG in `assets/` named by the `icon` field of the manifest. Without it the directory shows the generic plugin icon.

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
- **Install button:** add a one-click install button to your README:

    ```markdown
    [![Install in Kiosk Satellite](https://kiosksatellite.com/img/badges/install-plugin.svg)](https://kiosksatellite.com/redirect/plugin/?repository=owner/plugin)
    ```

- **Removal:** open a [Remove a plugin](https://github.com/jxlarrea/kiosk-satellite-plugins/issues/new?template=remove-plugin.yml) issue.

Active installs count kiosks that share usage analytics and only show once a plugin reaches 5.

## Create a plugin

Plugins are Java code that runs inside Kiosk Satellite. They can draw over the dashboard, add screensavers and voice skins and publish entities to Home Assistant. Start from Hello World, which already shows every feature of the SDK.

Read [Getting started](docs/getting-started.md) to build Hello World, change it into your own plugin and publish your first release.

## License

[Apache-2.0](LICENSE). Kiosk Satellite has its own application license.
