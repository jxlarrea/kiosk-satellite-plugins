#!/usr/bin/env python3
"""Check Community Plugins submissions and build the directory index.

The checks follow the ones Kiosk Satellite runs when it previews and installs a
plugin from GitHub, so a listed plugin installs. KS checks every setting again
when it installs a package. Only the Python standard library is used, so the
same file runs in GitHub Actions and on the kiosksatellite.com server.

    community_plugins.py check owner/repo
    community_plugins.py submission EVENT_JSON OUTPUT_MARKDOWN
    community_plugins.py removal EVENT_JSON OUTPUT_MARKDOWN
    community_plugins.py add owner/repo ID
    community_plugins.py remove owner/repo
    community_plugins.py index --out index.json --icons DIR [--previous index.json] [--installs installs.json]
"""
import argparse
import hashlib
import io
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LIST_PATH = ROOT / 'community' / 'plugins.json'
MANIFEST_NAME = 'kiosk-satellite-plugin.json'
MAX_PACKAGE_BYTES = 4 * 1024 * 1024
GITHUB_ACTIONS_BOT = (41898282, 'github-actions[bot]', 'Bot')
# Active installs below this stay hidden, so a small plugin cannot be traced to one home.
MIN_PUBLIC_INSTALLS = 5
MAX_ICON_BYTES = 256 * 1024
ICON_BASE = 'https://kiosksatellite.com/plugins/icons/'
ALLOWED_HOSTS = {
    'github.com',
    'api.github.com',
    'raw.githubusercontent.com',
    'release-assets.githubusercontent.com',
    'objects.githubusercontent.com',
}
CAPABILITIES = {
    'overlay', 'native', 'entities', 'host.read', 'host.control', 'shizuku',
    'screensaver', 'screensaver.mode', 'noise', 'ha', 'voice.skin', 'voice.text', 'camera',
    'barcode', 'ma',
}
SETTING_TYPES = {
    'string', 'secret', 'entity', 'icon', 'dashboard', 'cameraView', 'boolean',
    'color', 'number', 'select', 'list',
}
ID_PATTERN = re.compile(r'[a-z][a-z0-9]*(?:-[a-z0-9]+)*')
VERSION_PATTERN = re.compile(r'[0-9]+\.[0-9]+\.[0-9]+(?:-[a-zA-Z0-9.-]+)?')
TAG_PATTERN = re.compile(r'[A-Za-z0-9][A-Za-z0-9._-]{0,150}')
REPOSITORY_PATTERN = re.compile(r'[A-Za-z0-9][A-Za-z0-9-]*/[A-Za-z0-9_.-]+')
STEPS = [
    ('repository', 'Public repository with a license'),
    ('release', 'Stable release with assets published by GitHub Actions'),
    ('manifest', 'Manifest passes the SDK 1 checks'),
    ('package', 'Package matches its checksum and contains only plugin files'),
    ('readme', 'README.md at the release tag'),
    ('owner', 'Submitted by the repository owner'),
    ('directory', 'Not listed yet and the plugin ID is free'),
]


class CheckError(Exception):
    def __init__(self, step, message):
        super().__init__(message)
        self.step = step
        self.message = message


class _GitHubOnly(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        _require_github(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def _require_github(url):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != 'https' or parsed.username or parsed.port or parsed.hostname not in ALLOWED_HOSTS:
        raise ValueError('Download redirected outside GitHub')


class GitHub:
    """Reads GitHub with an optional token, which only ever goes to api.github.com."""

    def __init__(self, token=None, opener=None):
        self.token = token
        self.opener = opener or urllib.request.build_opener(_GitHubOnly)

    def get(self, url, limit, accept=None):
        _require_github(url)
        request = urllib.request.Request(url, headers={'User-Agent': 'Kiosk-Satellite-Community-Plugins'})
        if accept:
            request.add_header('Accept', accept)
        if self.token and urllib.parse.urlsplit(url).hostname == 'api.github.com':
            # Unredirected headers stay off the request a redirect makes.
            request.add_unredirected_header('Authorization', f'Bearer {self.token}')
        try:
            with self.opener.open(request, timeout=30) as response:
                data = response.read(limit + 1)
        except urllib.error.HTTPError as error:
            if error.code == 404:
                raise LookupError('not found') from error
            if error.code in (403, 429):
                raise RuntimeError('GitHub denied the request or its request limit was reached') from error
            raise RuntimeError(f'GitHub request failed ({error.code})') from error
        if len(data) > limit:
            raise ValueError('File exceeds the size limit')
        return data

    def api(self, path, limit=256 * 1024, accept=None):
        data = self.get(f'https://api.github.com{path}', limit, accept)
        return data if accept else json.loads(data)

    def is_public_member(self, org, user):
        try:
            self.get(f'https://api.github.com/orgs/{org}/public_members/{user}', 1024)
            return True
        except LookupError:
            return False


def parse_repository(text):
    """owner/repo from a github.com URL or an owner/repo pair, the way KS accepts it."""
    text = (text or '').strip()
    if not text.startswith('https://'):
        text = f'https://github.com/{text}'
    parsed = urllib.parse.urlsplit(text)
    if (parsed.scheme != 'https' or (parsed.hostname or '').lower() != 'github.com' or parsed.username
            or parsed.port or parsed.query or parsed.fragment):
        raise ValueError('Use a public https://github.com/owner/repository URL')
    path = re.sub(r'\.git$', '', re.sub(r'/$', '', parsed.path)).lstrip('/')
    if not REPOSITORY_PATTERN.fullmatch(path) or path.split('/')[1] in ('.', '..'):
        raise ValueError('Use the repository URL without a file or branch path')
    return path.lower()


def _text(data, key, limit, where='manifest'):
    value = data.get(key)
    if not isinstance(value, str) or not value.strip() or len(value) > limit:
        raise ValueError(f'Invalid {key} in the {where}')
    return value


def _integer(value):
    return isinstance(value, int) and not isinstance(value, bool)


def validate_manifest(manifest):
    """The SDK 1 manifest rules from PluginManifest.kt, down to each setting's key and type."""
    if not isinstance(manifest, dict):
        raise ValueError('The manifest must be a JSON object')
    if manifest.get('schemaVersion') != 1 or not _integer(manifest.get('schemaVersion')):
        raise ValueError('Unsupported manifest schema')
    if manifest.get('apiVersion') != 1 or not _integer(manifest.get('apiVersion')):
        raise ValueError('This plugin needs a different SDK version')
    if not ID_PATTERN.fullmatch(_text(manifest, 'id', 64)):
        raise ValueError('Invalid plugin ID')
    _text(manifest, 'name', 80)
    if not VERSION_PATTERN.fullmatch(_text(manifest, 'version', 40)):
        raise ValueError('Invalid version')
    if not re.fullmatch(r'[a-zA-Z_$][a-zA-Z0-9_$]*(?:\.[a-zA-Z_$][a-zA-Z0-9_$]*)+', _text(manifest, 'entryClass', 200)):
        raise ValueError('Invalid entry class')
    sdk = manifest.get('minAndroidSdk')
    if not _integer(sdk) or sdk < 24:
        raise ValueError('Minimum Android SDK must be at least 24')
    capabilities = manifest.get('capabilities')
    if not isinstance(capabilities, list) or not all(isinstance(c, str) for c in capabilities):
        raise ValueError('Capabilities must be a list of names')
    caps = set(capabilities)
    if not caps <= CAPABILITIES:
        raise ValueError(f'Unsupported plugin capability: {", ".join(sorted(caps - CAPABILITIES))}')
    if ('noise' in caps or 'voice.text' in caps) and 'host.read' not in caps:
        raise ValueError('noise and voice.text need the host.read capability')
    if 'voice.skin' in caps and not {'overlay', 'host.read'} <= caps:
        raise ValueError('voice.skin needs the overlay and host.read capabilities')
    for key, limit in (('description', 1000), ('author', 120), ('license', 120)):
        _text(manifest, key, limit)
    headless = manifest.get('headless')
    if headless is not None and not isinstance(headless, bool):
        raise ValueError('Invalid headless')
    icon = manifest.get('icon')
    if icon is not None and (not isinstance(icon, str) or not valid_asset_path(icon) or not icon.lower().endswith('.png')):
        raise ValueError('Invalid icon. Name a PNG under assets/')
    thumbnail = manifest.get('voiceSkinThumbnail')
    if thumbnail is not None:
        if (not isinstance(thumbnail, str) or not valid_asset_path(thumbnail)
                or thumbnail.lower().rsplit('.', 1)[-1] not in ('png', 'jpg', 'jpeg', 'webp')):
            raise ValueError('Invalid voiceSkinThumbnail')
        if 'voice.skin' not in caps:
            raise ValueError('voiceSkinThumbnail needs the voice.skin capability')
    for key, limit in (('settings', 50), ('commands', 20), ('triggers', 20), ('buttons', 20), ('groups', 20)):
        value = manifest.get(key, [])
        if not isinstance(value, list) or len(value) > limit:
            raise ValueError(f'{key} must be a list of at most {limit}')
    keys = set()
    for setting in manifest.get('settings', []):
        if not isinstance(setting, dict):
            raise ValueError('Invalid setting')
        key = _text(setting, 'key', 64, 'settings')
        if not re.fullmatch(r'[a-zA-Z][a-zA-Z0-9_]*', key) or key in keys:
            raise ValueError('Invalid or duplicate setting key')
        keys.add(key)
        _text(setting, 'title', 80, 'settings')
        if setting.get('type') not in SETTING_TYPES:
            raise ValueError(f'Unsupported setting type for {key}')
        if setting.get('type') == 'secret' and setting.get('default') != '':
            raise ValueError('Secret settings must default to an empty string')
    for kind in ('commands', 'triggers', 'buttons'):
        ids = set()
        for item in manifest.get(kind, []):
            if not isinstance(item, dict):
                raise ValueError(f'Invalid {kind} entry')
            item_id = _text(item, 'id', 64, kind)
            if not re.fullmatch(r'[a-z][a-zA-Z0-9]*', item_id) or item_id in ids:
                raise ValueError(f'Invalid or duplicate {kind} ID')
            ids.add(item_id)
            _text(item, 'title', 80, kind)
            if kind == 'buttons':
                _text(item, 'label', 40, kind)


def valid_asset_path(path):
    return 1 <= len(path) <= 240 and all(
        part not in ('.', '..') and re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}', part) for part in path.split('/')
    )


def png_size(data):
    """A PNG's width and height from its header, or None for anything else."""
    if len(data) < 24 or data[:8] != b'\x89PNG\r\n\x1a\n' or data[12:16] != b'IHDR':
        return None
    return int.from_bytes(data[16:20], 'big'), int.from_bytes(data[20:24], 'big')


def validate_package(data, manifest):
    """The package rules from PluginPackage.kt: allowed entries, DEX only code and the reviewed manifest.

    Returns the icon's bytes, or None when the manifest names no icon.
    """
    if not 1 <= len(data) <= MAX_PACKAGE_BYTES:
        raise ValueError('Plugin ZIP must be at most 4 MB')
    required = {MANIFEST_NAME, 'plugin.jar', 'LICENSE'}
    native = re.compile(r'native/(arm64-v8a|armeabi-v7a|x86_64)/lib[a-zA-Z0-9_]+\.so')
    try:
        archive = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile as error:
        raise ValueError('The package is not a ZIP file') from error
    seen, expanded, natives = set(), 0, []
    with archive:
        for entry in archive.infolist():
            name = entry.filename
            allowed = name in required or native.fullmatch(name) or (
                name.startswith('assets/') and valid_asset_path(name[len('assets/'):]))
            if entry.is_dir() or not allowed or name in seen:
                raise ValueError(f'Unexpected or duplicate ZIP entry: {name}')
            seen.add(name)
            if len(seen) > 512:
                raise ValueError('At most 512 package files are supported')
            expanded += entry.file_size
            if expanded > MAX_PACKAGE_BYTES:
                raise ValueError('Expanded plugin exceeds 4 MB')
            if name == MANIFEST_NAME and entry.file_size > 32768:
                raise ValueError('Manifest exceeds 32 KB')
            if native.fullmatch(name):
                natives.append(name)
        if not required <= seen:
            raise ValueError('Package needs kiosk-satellite-plugin.json, plugin.jar and LICENSE')
        if natives and 'native' not in manifest.get('capabilities', []):
            raise ValueError('Native libraries require the native capability')
        if len(natives) > 12:
            raise ValueError('At most 12 native libraries are supported')
        for name in natives:
            header = archive.read(name)[:20]
            abi = name.split('/')[1]
            machine = header[18] | (header[19] << 8) if len(header) == 20 else -1
            expected = {'arm64-v8a': 183, 'armeabi-v7a': 40}.get(abi, 62)
            if (len(header) != 20 or header[:4] != b'\x7fELF' or header[5] != 1 or machine != expected
                    or header[4] != (1 if abi == 'armeabi-v7a' else 2)):
                raise ValueError(f'Native library ABI does not match its directory: {name}')
        try:
            packaged = json.loads(archive.read(MANIFEST_NAME))
        except ValueError as error:
            raise ValueError('The packaged manifest is not valid JSON') from error
        if packaged != manifest:
            raise ValueError('Package manifest does not match the release manifest')
        _validate_dex(archive.read('plugin.jar'))
        icon = manifest.get('icon')
        if icon is None:
            return None
        if f'assets/{icon}' not in seen:
            raise ValueError(f'The icon assets/{icon} is not in the package')
        data = archive.read(f'assets/{icon}')
        if len(data) > MAX_ICON_BYTES or png_size(data) != (256, 256):
            raise ValueError('The icon must be a 256 by 256 PNG of at most 256 KB')
        return data


def _validate_dex(jar):
    try:
        archive = zipfile.ZipFile(io.BytesIO(jar))
    except zipfile.BadZipFile as error:
        raise ValueError('plugin.jar is not a JAR file') from error
    seen, expanded = set(), 0
    with archive:
        for entry in archive.infolist():
            if not re.fullmatch(r'classes(?:[2-9]|[1-9][0-9]+)?\.dex', entry.filename) or entry.filename in seen:
                raise ValueError('plugin.jar must contain only DEX files')
            seen.add(entry.filename)
            body = archive.read(entry)
            if not body:
                raise ValueError('Empty DEX file')
            if body[:4] != b'dex\n':
                raise ValueError('Invalid DEX header')
            expanded += len(body)
            if expanded > MAX_PACKAGE_BYTES:
                raise ValueError('Expanded DEX exceeds 4 MB')
    if 'classes.dex' not in seen:
        raise ValueError('plugin.jar has no classes.dex')


def _step(name, action):
    try:
        return action()
    except CheckError:
        raise
    except (ValueError, LookupError, RuntimeError, OSError) as error:
        message = str(error)
        if isinstance(error, LookupError):
            message = 'GitHub returned not found'
        raise CheckError(name, message) from error


def check_plugin(github, repository, previous=None):
    """Everything KS checks before it installs, plus the repository facts the directory shows.

    A previous index entry with the same release digest skips the package download.
    """
    repository = parse_repository(repository)

    def repo_facts():
        repo = github.api(f'/repos/{repository}')
        if repo.get('private') or repo.get('visibility', 'public') != 'public':
            raise ValueError('The repository is not public')
        if repo.get('archived') or repo.get('disabled'):
            raise ValueError('The repository is archived')
        if not repo.get('license'):
            raise ValueError('The repository has no license file GitHub recognizes')
        return repo

    repo = _step('repository', repo_facts)
    full_name = repo['full_name']

    def release_facts():
        release = github.api(f'/repos/{repository}/releases/latest')
        if release.get('draft') is not False or release.get('prerelease') is not False or not isinstance(release.get('assets'), list):
            raise ValueError('GitHub did not return a published stable release')
        if not isinstance(release.get('tag_name'), str) or not TAG_PATTERN.fullmatch(release['tag_name']):
            raise ValueError('Invalid release tag')
        return release

    release = _step('release', release_facts)
    tag = release['tag_name']

    def asset(name, limit):
        matches = [a for a in release['assets'] if isinstance(a, dict) and a.get('name') == name]
        if len(matches) != 1 or matches[0].get('state') != 'uploaded':
            raise ValueError(f'The release needs exactly one uploaded {name} asset')
        found = matches[0]
        uploader = found.get('uploader') or {}
        if (uploader.get('id'), uploader.get('login'), uploader.get('type')) != GITHUB_ACTIONS_BOT:
            raise ValueError(f'{name} must be published by GitHub Actions. Manually uploaded files are not supported')
        if not _integer(found.get('size')) or not 0 < found['size'] <= limit:
            raise ValueError(f'{name} exceeds the size limit or is empty')
        url = urllib.parse.urlsplit(found.get('browser_download_url') or '')
        parts = url.path.strip('/').split('/')
        if (url.scheme != 'https' or url.hostname != 'github.com' or url.query or url.fragment or len(parts) != 6
                or '/'.join(parts[:2]).lower() != repository or '/'.join(parts[2:]) != f'releases/download/{tag}/{name}'):
            raise ValueError(f'Invalid release URL for {name}')
        return found

    manifest_asset = _step('release', lambda: asset(MANIFEST_NAME, 32 * 1024))

    def manifest_facts():
        manifest = json.loads(github.get(manifest_asset['browser_download_url'], 32 * 1024))
        validate_manifest(manifest)
        return manifest

    manifest = _step('manifest', manifest_facts)
    package_name = f'{manifest["id"]}-{manifest["version"]}.zip'
    package = _step('release', lambda: asset(package_name, MAX_PACKAGE_BYTES))
    checksum_asset = _step('release', lambda: asset(f'{package_name}.sha256', 1024))

    def package_facts():
        line = github.get(checksum_asset['browser_download_url'], 1024).decode('utf-8').strip()
        match = re.fullmatch(r'([a-f0-9]{64}) [ *](.+)', line)
        if not match or match.group(2) != package_name:
            raise ValueError('Invalid release checksum or package filename')
        digest = match.group(1)
        if package.get('digest') != f'sha256:{digest}':
            raise ValueError('The release checksum must match the GitHub asset SHA-256 digest')
        if previous and previous.get('sha256') == digest and previous.get('status') == 'ok':
            return digest, None, True
        data = github.get(package['browser_download_url'], MAX_PACKAGE_BYTES)
        if hashlib.sha256(data).hexdigest() != digest:
            raise ValueError('The package does not match its checksum')
        return digest, validate_package(data, manifest), False

    digest, icon, unchanged = _step('package', package_facts)

    def readme_facts():
        ref = github.api(f'/repos/{repository}/commits/refs/tags/{urllib.parse.quote(tag)}', 128,
                         accept='application/vnd.github.sha').decode().strip()
        if not re.fullmatch(r'[a-f0-9]{40}', ref):
            raise ValueError('GitHub did not return the release tag revision')
        base = f'https://raw.githubusercontent.com/{full_name}/{ref}/'
        github.get(base + 'README.md', 128 * 1024)
        return base

    readme_base = _step('readme', readme_facts)
    owner = repo.get('owner') or {}
    return {
        'id': manifest['id'],
        'name': manifest['name'],
        'description': manifest['description'],
        'author': manifest['author'],
        'license': manifest['license'],
        'version': manifest['version'],
        'tag': tag,
        'released': release.get('published_at'),
        'minAndroidSdk': manifest['minAndroidSdk'],
        'headless': manifest.get('headless') is True,
        'capabilities': sorted(manifest['capabilities']),
        'repository': f'https://github.com/{full_name}',
        'owner': owner.get('login'),
        'ownerType': owner.get('type'),
        'ownerAvatar': owner.get('avatar_url'),
        'stars': repo.get('stargazers_count', 0),
        'readme': readme_base + 'README.md',
        'readmeBaseUrl': readme_base,
        'sha256': digest,
        # Left out of the index: build_index publishes the icon and links it.
        'iconBytes': icon,
        'unchanged': unchanged,
    }


def downloads(github, repository, plugin_id):
    """Package downloads across every release. Updates count, fleet followers do not download."""
    total, page = 0, 1
    pattern = re.compile(re.escape(plugin_id) + r'-.+\.zip')
    while page <= 10:
        releases = github.api(f'/repos/{repository}/releases?per_page=100&page={page}', 4 * 1024 * 1024)
        for release in releases:
            for item in release.get('assets', []):
                if pattern.fullmatch(item.get('name', '')):
                    total += item.get('download_count', 0)
        if len(releases) < 100:
            break
        page += 1
    return total


def load_list(path=LIST_PATH):
    entries = json.loads(Path(path).read_text())
    if not isinstance(entries, list):
        raise ValueError('community/plugins.json must be a list')
    return entries


def save_list(entries, path=LIST_PATH):
    entries = sorted(entries, key=lambda e: e['repository'])
    Path(path).write_text(json.dumps(entries, indent=2) + '\n')


def check_submission(github, repository, author, maintainer, entries):
    """The checklist for one submission: (results, facts). Results map each step to None or its error."""
    results = {name: None for name, _ in STEPS}
    facts = None
    try:
        repository = _step('repository', lambda: parse_repository(repository))
        facts = check_plugin(github, repository)
    except CheckError as error:
        index = [name for name, _ in STEPS].index(error.step)
        results[error.step] = error.message
        for name, _ in STEPS[index + 1:]:
            results[name] = 'Not checked'
        return results, None
    owner = facts['owner'] or ''
    if author.lower() not in (owner.lower(), maintainer.lower()) and not (
            facts['ownerType'] == 'Organization' and github.is_public_member(owner, author)):
        results['owner'] = f'@{author} does not own this repository. Submit it from the {owner} account'
        if facts['ownerType'] == 'Organization':
            results['owner'] += f' or make your membership of {owner} public'
    listed = {e['repository'].lower() for e in entries}
    taken = {e['id']: e['repository'] for e in entries}
    if repository in listed:
        results['directory'] = 'This repository is already listed'
    elif facts['id'] in taken:
        results['directory'] = f'The plugin ID {facts["id"]} belongs to {taken[facts["id"]]}. Pick another ID'
    return results, facts


def submission_report(results, facts):
    lines = ['<!-- community-plugins-check -->', '## Community Plugins check', '']
    for name, title in STEPS:
        error = results[name]
        if error is None:
            lines.append(f'- ✅ {title}')
        elif error == 'Not checked':
            lines.append(f'- ⏸️ {title}: not checked')
        else:
            lines.append(f'- ❌ {title}: {error}')
    lines.append('')
    if all(error is None for error in results.values()):
        lines.append(f'**{facts["name"]}** {facts["version"]} by {facts["author"]} passed every check. '
                     'A maintainer reviews it next.')
    else:
        lines.append('Fix the items above, then edit this issue to run the check again.')
    return '\n'.join(lines) + '\n'


def form_field(body, heading):
    """A field's answer in a rendered issue form body."""
    match = re.search(rf'^###\s+{re.escape(heading)}\s*$\n+(.*?)(?=^###\s|\Z)', body or '', re.M | re.S)
    answer = match.group(1).strip() if match else ''
    return '' if answer == '_No response_' else answer


def check_removal(github, repository, author, maintainer, entries):
    """(report, repository) for a removal request. A maintainer still approves every removal."""
    lines = ['<!-- community-plugins-check -->', '## Community Plugins removal', '']
    try:
        repository = parse_repository(repository)
    except ValueError as error:
        lines.append(f'- ❌ {error}')
        return '\n'.join(lines) + '\n', None
    entry = next((e for e in entries if e['repository'].lower() == repository), None)
    if not entry:
        lines.append(f'- ❌ {repository} is not listed')
        return '\n'.join(lines) + '\n', None
    lines.append(f'- ✅ {entry["repository"]} ({entry["id"]}) is listed')
    try:
        owner = github.api(f'/repos/{repository}')['owner']
    except (LookupError, RuntimeError, OSError):
        owner = None
    if owner is None:
        lines.append('- ⚠️ The repository is gone or unreadable')
    elif author.lower() in (owner['login'].lower(), maintainer.lower()) or (
            owner.get('type') == 'Organization' and github.is_public_member(owner['login'], author)):
        lines.append('- ✅ Requested by the repository owner')
    else:
        lines.append(f'- ⚠️ @{author} does not own the repository. A maintainer decides')
    lines += ['', 'A maintainer reviews the request next.']
    return '\n'.join(lines) + '\n', entry['repository']


def build_index(github, entries, previous=None, installs=None, now=None, icons=None, icon_base=ICON_BASE):
    """The directory index. Icons are written to the icons folder named by their content, so a new
    icon gets a new address and no cache keeps showing the old one."""
    previous = {p['repository'].lower(): p for p in (previous or {}).get('plugins', [])}
    installs = installs or {}
    icons = Path(icons) if icons else None
    plugins = []
    for entry in entries:
        url = f'https://github.com/{entry["repository"]}'
        before = previous.get(url.lower())
        item = {'id': entry['id'], 'repository': url, 'added': entry.get('added')}
        try:
            facts = check_plugin(github, entry['repository'], before)
            if facts['id'] != entry['id']:
                raise CheckError('manifest', f'The plugin ID changed from {entry["id"]} to {facts["id"]}')
            icon = facts.pop('iconBytes')
            unchanged = facts.pop('unchanged')
            item.update(facts, added=entry.get('added'), status='ok', icon=None)
            if icon and icons:
                name = f'{entry["id"]}-{hashlib.sha256(icon).hexdigest()[:12]}.png'
                icons.mkdir(parents=True, exist_ok=True)
                (icons / name).write_bytes(icon)
                item['icon'] = icon_base + name
            elif unchanged and before and before.get('icon') and icons and (icons / before['icon'].rsplit('/', 1)[-1]).exists():
                item['icon'] = before['icon']
            item['downloads'] = downloads(github, entry['repository'], entry['id'])
        except (CheckError, ValueError, RuntimeError, OSError) as error:
            # Keep what the last good run knew, so the listing still names the plugin.
            if before:
                item.update({k: v for k, v in before.items() if k not in ('status', 'problem')})
            item.update(status='broken', problem=getattr(error, 'message', str(error)))
        count = installs.get(entry['id'])
        item['installs'] = count if isinstance(count, int) and count >= MIN_PUBLIC_INSTALLS else None
        plugins.append(item)
    if icons and icons.is_dir():
        kept = {p['icon'].rsplit('/', 1)[-1] for p in plugins if p.get('icon')}
        for file in icons.glob('*.png'):
            if file.name not in kept:
                file.unlink()
    stamp = (now or datetime.now(timezone.utc)).strftime('%Y-%m-%dT%H:%M:%SZ')
    return {'schemaVersion': 1, 'generated': stamp, 'plugins': plugins}


def _github():
    return GitHub(os.environ.get('GITHUB_TOKEN') or os.environ.get('GH_TOKEN'))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest='command', required=True)
    check = commands.add_parser('check', help='check one repository like a submission')
    check.add_argument('repository')
    submission = commands.add_parser('submission', help='check the issue in a GitHub event')
    submission.add_argument('event')
    submission.add_argument('report')
    removal = commands.add_parser('removal', help='check the removal request in a GitHub event')
    removal.add_argument('event')
    removal.add_argument('report')
    add = commands.add_parser('add', help='list a repository')
    add.add_argument('repository')
    add.add_argument('id')
    remove = commands.add_parser('remove', help='unlist a repository')
    remove.add_argument('repository')
    index = commands.add_parser('index', help='build the directory index')
    index.add_argument('--list', default=str(LIST_PATH))
    index.add_argument('--out', required=True)
    index.add_argument('--previous')
    index.add_argument('--installs', help='JSON object of plugin ID to active installs')
    index.add_argument('--icons', help='folder the plugin icons are published from')
    index.add_argument('--icon-base', default=ICON_BASE, help='address of that folder')
    args = parser.parse_args(argv)

    if args.command == 'check':
        results, facts = check_submission(_github(), args.repository, '', '', load_list())
        results['owner'] = None
        print(submission_report(results, facts))
        return 0 if all(e is None for e in results.values()) else 1
    if args.command == 'submission':
        event = json.loads(Path(args.event).read_text())
        issue = event['issue']
        maintainer = event['repository']['owner']['login']
        results, facts = check_submission(_github(), form_field(issue.get('body'), 'Repository'),
                                          issue['user']['login'], maintainer, load_list())
        Path(args.report).write_text(submission_report(results, facts))
        passed = all(e is None for e in results.values())
        output = os.environ.get('GITHUB_OUTPUT')
        if output:
            with open(output, 'a') as handle:
                handle.write(f'passed={"true" if passed else "false"}\n')
                handle.write(f'repository={facts["repository"].removeprefix("https://github.com/") if facts else ""}\n')
                handle.write(f'id={facts["id"] if facts else ""}\n')
        return 0
    if args.command == 'removal':
        event = json.loads(Path(args.event).read_text())
        issue = event['issue']
        report, repository = check_removal(_github(), form_field(issue.get('body'), 'Repository'), issue['user']['login'],
                                           event['repository']['owner']['login'], load_list())
        Path(args.report).write_text(report)
        output = os.environ.get('GITHUB_OUTPUT')
        if output:
            with open(output, 'a') as handle:
                handle.write(f'passed={"true" if repository else "false"}\nrepository={repository or ""}\n')
        return 0
    if args.command == 'add':
        entries = load_list()
        repository = parse_repository(args.repository)
        if any(e['repository'].lower() == repository for e in entries) or any(e['id'] == args.id for e in entries):
            raise SystemExit('Already listed')
        entries.append({'repository': args.repository.removeprefix('https://github.com/'), 'id': args.id,
                        'added': datetime.now(timezone.utc).strftime('%Y-%m-%d')})
        save_list(entries)
        return 0
    if args.command == 'remove':
        repository = parse_repository(args.repository)
        entries = load_list()
        kept = [e for e in entries if e['repository'].lower() != repository]
        if len(kept) == len(entries):
            raise SystemExit('Not listed')
        save_list(kept)
        return 0
    previous = json.loads(Path(args.previous).read_text()) if args.previous and Path(args.previous).exists() else None
    installs = json.loads(Path(args.installs).read_text()) if args.installs else None
    result = build_index(_github(), load_list(args.list), previous, installs, icons=args.icons, icon_base=args.icon_base)
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    temporary = out.with_suffix('.tmp')
    temporary.write_text(json.dumps(result, indent=1, ensure_ascii=False) + '\n')
    temporary.replace(out)
    broken = [p['id'] for p in result['plugins'] if p['status'] != 'ok']
    print(f'{len(result["plugins"])} plugins, {len(broken)} broken{": " + ", ".join(broken) if broken else ""}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
