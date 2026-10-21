"""Check the Community Plugins submission checks and index without the network."""
import hashlib
import io
import json
import struct
import tempfile
import unittest
from pathlib import Path
import zipfile

import community_plugins as cp

REPO = 'alice/kiosk-weather'
TAG = 'v1.2.0'
SHA = 'a' * 40


def manifest(**overrides):
    data = {
        'schemaVersion': 1, 'apiVersion': 1, 'id': 'weather', 'name': 'Weather', 'version': '1.2.0',
        'entryClass': 'me.alice.weather.WeatherPlugin', 'minAndroidSdk': 26, 'capabilities': ['overlay'],
        'description': 'Shows the weather.', 'author': 'Alice', 'license': 'MIT', 'settings': [],
    }
    data.update(overrides)
    return data


def jar(files=None):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w') as archive:
        for name, body in (files or {'classes.dex': b'dex\n035\0'}).items():
            archive.writestr(name, body)
    return buffer.getvalue()


def package(data=None, extra=None):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w') as archive:
        archive.writestr('kiosk-satellite-plugin.json', json.dumps(data or manifest()))
        archive.writestr('plugin.jar', jar())
        archive.writestr('LICENSE', 'MIT')
        for name, body in (extra or {}).items():
            archive.writestr(name, body)
    return buffer.getvalue()


def png(width=256, height=256):
    return b'\x89PNG\r\n\x1a\n' + b'\0\0\0\rIHDR' + struct.pack('>II', width, height) + b'\x08\x06\0\0\0' + b'\0' * 20


def asset(name, size, digest=None, uploader=cp.GITHUB_ACTIONS_BOT, downloads=0):
    return {
        'name': name, 'state': 'uploaded', 'size': size, 'digest': digest, 'download_count': downloads,
        'uploader': {'id': uploader[0], 'login': uploader[1], 'type': uploader[2]},
        'browser_download_url': f'https://github.com/{REPO}/releases/download/{TAG}/{name}',
    }


class FakeGitHub:
    def __init__(self, data=None, zip_bytes=None, owner='alice', owner_type='User', members=()):
        self.manifest = data or manifest()
        self.zip = zip_bytes or package(self.manifest)
        self.members = set(members)
        self.downloaded = []
        digest = hashlib.sha256(self.zip).hexdigest()
        name = f'{self.manifest["id"]}-{self.manifest["version"]}.zip'
        self.repo = {
            'full_name': 'Alice/kiosk-weather', 'private': False, 'visibility': 'public', 'archived': False,
            'license': {'spdx_id': 'MIT'}, 'stargazers_count': 9,
            'owner': {'login': owner, 'type': owner_type, 'avatar_url': 'https://avatars.example/a'},
        }
        self.release = {
            'draft': False, 'prerelease': False, 'tag_name': TAG, 'published_at': '2026-10-01T00:00:00Z',
            'assets': [
                asset('kiosk-satellite-plugin.json', 100),
                asset(name, len(self.zip), f'sha256:{digest}', downloads=30),
                asset(f'{name}.sha256', 80),
            ],
        }
        self.files = {
            f'https://github.com/{REPO}/releases/download/{TAG}/kiosk-satellite-plugin.json': json.dumps(self.manifest).encode(),
            f'https://github.com/{REPO}/releases/download/{TAG}/{name}': self.zip,
            f'https://github.com/{REPO}/releases/download/{TAG}/{name}.sha256': f'{digest}  {name}\n'.encode(),
            f'https://raw.githubusercontent.com/Alice/kiosk-weather/{SHA}/README.md': b'# Weather',
        }

    def api(self, path, limit=0, accept=None):
        if path == f'/repos/{REPO}':
            return self.repo
        if path == f'/repos/{REPO}/releases/latest':
            return self.release
        if path.startswith(f'/repos/{REPO}/releases?'):
            older = {'assets': [{'name': 'weather-1.1.0.zip', 'download_count': 12}, {'name': 'weather-1.1.0.zip.sha256', 'download_count': 99}]}
            return [self.release, older]
        if path == f'/repos/{REPO}/commits/refs/tags/{TAG}':
            return SHA.encode()
        raise LookupError(path)

    def get(self, url, limit, accept=None):
        self.downloaded.append(url)
        if url not in self.files:
            raise LookupError(url)
        return self.files[url]

    def is_public_member(self, org, user):
        return user in self.members


class RepositoryTest(unittest.TestCase):
    def test_accepts_the_urls_ks_accepts(self):
        for text in ['https://github.com/Alice/Kiosk-Weather', 'https://github.com/alice/kiosk-weather/',
                     'https://github.com/alice/kiosk-weather.git', 'alice/kiosk-weather']:
            self.assertEqual(cp.parse_repository(text), REPO)

    def test_rejects_paths_queries_and_other_hosts(self):
        for text in ['https://gitlab.com/alice/x', 'http://github.com/alice/x', 'https://github.com/alice/x/tree/main',
                     'https://github.com/alice/x?tab=readme', 'https://github.com/alice', 'https://github.com/alice/..', '']:
            with self.subTest(text=text), self.assertRaises(ValueError):
                cp.parse_repository(text)


class ManifestTest(unittest.TestCase):
    def test_valid_manifest(self):
        cp.validate_manifest(manifest())
        cp.validate_manifest(manifest(capabilities=['camera', 'barcode', 'ma']))
        cp.validate_manifest(manifest(capabilities=['screensaver', 'screensaver.mode']))

    def test_rules_from_the_app(self):
        for change in [
            {'schemaVersion': True}, {'apiVersion': 2}, {'id': 'Weather'}, {'version': '1.2'}, {'entryClass': 'Plugin'},
            {'minAndroidSdk': 23}, {'capabilities': ['microphone']}, {'capabilities': ['noise']},
            {'capabilities': ['voice.skin', 'overlay']}, {'author': ' '}, {'headless': 'yes'},
            {'voiceSkinThumbnail': 'thumb.png'}, {'settings': [{'key': 'a', 'title': 'A', 'type': 'secret', 'default': 'x'}]},
            {'settings': [{'key': 'a', 'title': 'A', 'type': 'string'}, {'key': 'a', 'title': 'B', 'type': 'string'}]},
            {'commands': [{'id': 'Run', 'title': 'Run'}]}, {'buttons': [{'id': 'test', 'title': 'Test'}]},
        ]:
            with self.subTest(change=change), self.assertRaises(ValueError):
                cp.validate_manifest(manifest(**change))


class PackageTest(unittest.TestCase):
    def test_valid_package(self):
        cp.validate_package(package(extra={'assets/icon.png': b'png'}), manifest())

    def test_rejects_what_ks_rejects(self):
        cases = [
            package(extra={'classes.dex': b'dex\n'}),
            package(extra={'assets/../x': b'x'}),
            package(extra={'native/arm64-v8a/libx.so': b'\x7fELF' + b'\0' * 16}),
            package(manifest(version='9.9.9')),
        ]
        for data in cases:
            with self.assertRaises(ValueError):
                cp.validate_package(data, manifest())
        broken = io.BytesIO()
        with zipfile.ZipFile(broken, 'w') as archive:
            archive.writestr('kiosk-satellite-plugin.json', json.dumps(manifest()))
            archive.writestr('plugin.jar', jar({'Main.class': b'cafe'}))
            archive.writestr('LICENSE', 'MIT')
        with self.assertRaises(ValueError):
            cp.validate_package(broken.getvalue(), manifest())


class IconTest(unittest.TestCase):
    def test_the_icon_is_a_256_png_in_the_package(self):
        data = manifest(icon='icon.png')
        self.assertEqual(cp.validate_package(package(data, {'assets/icon.png': png()}), data), png())
        self.assertIsNone(cp.validate_package(package(), manifest()))
        for extra in [{}, {'assets/icon.png': png(512, 512)}, {'assets/icon.png': b'GIF89a' + b'\0' * 30}]:
            with self.assertRaises(ValueError):
                cp.validate_package(package(data, extra), data)
        for bad in ['../icon.png', 'icon.webp', 7]:
            with self.assertRaises(ValueError):
                cp.validate_manifest(manifest(icon=bad))

    def test_the_index_publishes_icons_by_content(self):
        entries = [{'repository': 'alice/kiosk-weather', 'id': 'weather'}]
        data = manifest(icon='icon.png')
        with tempfile.TemporaryDirectory() as folder:
            icons = Path(folder)
            (icons / 'gone-000000000000.png').write_bytes(b'old')
            first = cp.build_index(FakeGitHub(data, package(data, {'assets/icon.png': png()})), entries, icons=icons,
                                   icon_base='https://example.test/icons/')
            url = first['plugins'][0]['icon']
            self.assertRegex(url, r'^https://example.test/icons/weather-[0-9a-f]{12}\.png$')
            self.assertEqual([f.name for f in icons.iterdir()], [url.rsplit('/', 1)[1]])
            # An unchanged release keeps its icon without downloading the package again.
            again = cp.build_index(FakeGitHub(data, package(data, {'assets/icon.png': png()})), entries, previous=first,
                                   icons=icons, icon_base='https://example.test/icons/')
            self.assertEqual(again['plugins'][0]['icon'], url)
            self.assertNotIn('iconBytes', json.dumps(again))
            self.assertIsNone(cp.build_index(FakeGitHub(), entries, icons=icons)['plugins'][0]['icon'])
            self.assertEqual(list(icons.iterdir()), [])


class SubmissionTest(unittest.TestCase):
    def test_a_good_submission_passes_every_step(self):
        results, facts = cp.check_submission(FakeGitHub(), f'https://github.com/{REPO}', 'Alice', 'jxlarrea', [])
        self.assertTrue(all(error is None for error in results.values()), results)
        self.assertEqual(facts['repository'], 'https://github.com/Alice/kiosk-weather')
        self.assertEqual(facts['readmeBaseUrl'], f'https://raw.githubusercontent.com/Alice/kiosk-weather/{SHA}/')
        self.assertIn('passed every check', cp.submission_report(results, facts))

    def test_someone_else_cannot_submit_the_repository(self):
        results, _ = cp.check_submission(FakeGitHub(), REPO, 'mallory', 'jxlarrea', [])
        self.assertIn('does not own', results['owner'])
        results, _ = cp.check_submission(FakeGitHub(), REPO, 'jxlarrea', 'jxlarrea', [])
        self.assertIsNone(results['owner'])
        github = FakeGitHub(owner='weather-org', owner_type='Organization', members={'bob'})
        self.assertIsNone(cp.check_submission(github, REPO, 'bob', 'jxlarrea', [])[0]['owner'])

    def test_the_id_and_repository_stay_unique(self):
        listed = [{'repository': 'Alice/kiosk-weather', 'id': 'other'}]
        self.assertIn('already listed', cp.check_submission(FakeGitHub(), REPO, 'alice', 'x', listed)[0]['directory'])
        taken = [{'repository': 'bob/weather', 'id': 'weather'}]
        self.assertIn('bob/weather', cp.check_submission(FakeGitHub(), REPO, 'alice', 'x', taken)[0]['directory'])

    def test_manual_uploads_stop_the_check(self):
        github = FakeGitHub()
        github.release['assets'][0] = asset('kiosk-satellite-plugin.json', 100, uploader=(1, 'alice', 'User'))
        results, facts = cp.check_submission(github, REPO, 'alice', 'x', [])
        self.assertIn('GitHub Actions', results['release'])
        self.assertEqual(results['package'], 'Not checked')
        self.assertIsNone(facts)
        self.assertIn('edit this issue', cp.submission_report(results, facts))

    def test_a_tampered_package_fails(self):
        github = FakeGitHub()
        name = 'weather-1.2.0.zip'
        github.files[f'https://github.com/{REPO}/releases/download/{TAG}/{name}'] = package(manifest(name='Other'))
        self.assertIn('checksum', cp.check_submission(github, REPO, 'alice', 'x', [])[0]['package'])

    def test_reads_the_issue_form(self):
        body = '### Repository\n\nhttps://github.com/alice/kiosk-weather\n\n### Anything else\n\n_No response_\n'
        self.assertEqual(cp.form_field(body, 'Repository'), 'https://github.com/alice/kiosk-weather')
        self.assertEqual(cp.form_field(body, 'Anything else'), '')


class RemovalTest(unittest.TestCase):
    def test_the_owner_can_ask_for_removal(self):
        listed = [{'repository': 'Alice/kiosk-weather', 'id': 'weather'}]
        report, repository = cp.check_removal(FakeGitHub(), REPO, 'alice', 'jxlarrea', listed)
        self.assertEqual(repository, 'Alice/kiosk-weather')
        self.assertIn('Requested by the repository owner', report)
        report, _ = cp.check_removal(FakeGitHub(), REPO, 'mallory', 'jxlarrea', listed)
        self.assertIn('A maintainer decides', report)
        self.assertIsNone(cp.check_removal(FakeGitHub(), REPO, 'alice', 'jxlarrea', [])[1])


class IndexTest(unittest.TestCase):
    entries = [{'repository': 'alice/kiosk-weather', 'id': 'weather', 'added': '2026-10-02'}]

    def test_entries_carry_stats(self):
        index = cp.build_index(FakeGitHub(), self.entries, installs={'weather': 41})
        plugin = index['plugins'][0]
        self.assertEqual((plugin['status'], plugin['stars'], plugin['downloads'], plugin['installs']), ('ok', 9, 42, 41))
        self.assertEqual(plugin['added'], '2026-10-02')

    def test_small_install_counts_stay_hidden(self):
        self.assertIsNone(cp.build_index(FakeGitHub(), self.entries, installs={'weather': 4})['plugins'][0]['installs'])

    def test_an_unchanged_release_skips_the_package_download(self):
        first = cp.build_index(FakeGitHub(), self.entries)
        github = FakeGitHub()
        cp.build_index(github, self.entries, previous=first)
        self.assertFalse(any(url.endswith('.zip') for url in github.downloaded))

    def test_a_broken_plugin_keeps_its_last_listing(self):
        first = cp.build_index(FakeGitHub(), self.entries)
        github = FakeGitHub()
        github.repo['archived'] = True
        plugin = cp.build_index(github, self.entries, previous=first)['plugins'][0]
        self.assertEqual((plugin['status'], plugin['name']), ('broken', 'Weather'))
        self.assertIn('archived', plugin['problem'])

    def test_a_changed_id_breaks_the_entry(self):
        github = FakeGitHub(manifest(id='weather-two'))
        plugin = cp.build_index(github, self.entries)['plugins'][0]
        self.assertEqual(plugin['status'], 'broken')
        self.assertEqual(plugin['id'], 'weather')


class TokenTest(unittest.TestCase):
    def test_the_token_only_goes_to_the_api(self):
        sent = []

        class Opener:
            def open(self, request, timeout):
                sent.append((request.full_url, request.get_header('Authorization'), request.unredirected_hdrs))

                class Response(io.BytesIO):
                    def __enter__(self):
                        return self

                    def __exit__(self, *args):
                        return False
                return Response(b'{}')

        github = cp.GitHub('secret', Opener())
        github.api('/repos/a/b')
        github.get('https://raw.githubusercontent.com/a/b/c/README.md', 10)
        self.assertEqual(sent[0][2].get('Authorization'), 'Bearer secret')
        self.assertNotIn('Authorization', sent[1][2])
        with self.assertRaises(ValueError):
            github.get('https://example.com/x', 10)


if __name__ == '__main__':
    unittest.main()
