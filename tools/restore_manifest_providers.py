#!/usr/bin/env python3
"""Restore accidentally overwritten providers from this repo's own git history.

Only missing <provider> declarations are restored. All other current manifest
content, including MN StreamX branding, is left untouched. No provider class,
authority, exported flag, or FileProvider paths are guessed.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID_NS = 'http://schemas.android.com/apk/res/android'
TOOLS_NS = 'http://schemas.android.com/tools'
ET.register_namespace('android', ANDROID_NS)
ET.register_namespace('tools', TOOLS_NS)
ANDROID_NAME = f'{{{ANDROID_NS}}}name'
EXPORTED = f'{{{ANDROID_NS}}}exported'
MANIFEST_PATH = 'app/src/main/AndroidManifest.xml'


def xml_root(source: str) -> ET.Element:
    root = ET.fromstring(source)
    if root.tag != 'manifest' and not root.tag.endswith('}manifest'):
        raise ValueError('Not an Android manifest')
    return root


def provider_map(source: str) -> dict[str, ET.Element]:
    app = xml_root(source).find('application')
    if app is None:
        raise ValueError('Missing <application> element')
    return {
        name: provider
        for provider in app.findall('provider')
        if (name := provider.get(ANDROID_NAME))
    }


def git_output(repo: Path, *args: str) -> str:
    result = subprocess.run(
        ['git', *args], cwd=repo, text=True, capture_output=True,
        check=False, timeout=25,
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or f'git {args[0]} failed')
    return result.stdout


def most_recent_history_providers(repo: Path, current_names: set[str]) -> tuple[str, dict[str, ET.Element]]:
    commits = git_output(repo, 'log', '--format=%H', '--', MANIFEST_PATH).splitlines()
    if len(commits) < 2:
        raise RuntimeError(
            'No useful manifest history in Git. Use the GitHub History tab to restore '
            'the manifest before the branding update; do not guess provider declarations.'
        )
    for revision in commits[1:]:
        historical = git_output(repo, 'show', f'{revision}:{MANIFEST_PATH}')
        try:
            providers = provider_map(historical)
        except (ET.ParseError, ValueError):
            continue
        if set(providers) - current_names:
            return revision, providers
    raise RuntimeError(
        'Could not find missing provider declarations in prior manifest revisions. '
        'The current manifest was not changed.'
    )


def validate_provider(provider: ET.Element, repo: Path) -> None:
    name = provider.get(ANDROID_NAME, '')
    if provider.get(EXPORTED) != 'false':
        raise ValueError(
            f'Refusing to import provider {name}: expected android:exported="false". '
            'Check the historical declaration manually.'
        )
    for descendant in provider.iter('meta-data'):
        resource = descendant.get(f'{{{ANDROID_NS}}}resource', '')
        if resource.startswith('@xml/'):
            filename = resource.removeprefix('@xml/') + '.xml'
            if not (repo / 'app/src/main/res/xml' / filename).is_file():
                raise ValueError(f'Missing provider paths XML resource: {filename}')


def append_missing_providers(current: str, providers: dict[str, ET.Element], repo: Path) -> tuple[str, list[str]]:
    installed = provider_map(current)
    missing = {name: el for name, el in providers.items() if name not in installed}
    if not missing:
        return current, []
    snippets = []
    for name, provider in missing.items():
        validate_provider(provider, repo)
        ET.indent(provider, space='    ')
        snippet = ET.tostring(provider, encoding='unicode')
        # The manifest root already declares these namespace prefixes.
        snippet = snippet.replace(f' xmlns:android="{ANDROID_NS}"', '')
        snippet = snippet.replace(f' xmlns:tools="{TOOLS_NS}"', '')
        snippets.append('\n'.join('        ' + line for line in snippet.splitlines()))
    end = re.search(r'(?m)^[ \t]*</application>', current)
    if end is None:
        raise ValueError('Missing closing </application> tag')
    updated = current[:end.start()] + '\n'.join(snippets) + '\n' + current[end.start():]
    # Validate the exact output and forbid accidental modification to branding.
    parsed = provider_map(updated)
    if not all(name in parsed for name in missing):
        raise AssertionError('Restored providers disappeared after serialization')
    for token in ('@drawable/mn_streamx_tv_banner', '@mipmap/ic_launcher'):
        if token in current and token not in updated:
            raise AssertionError(f'Branding was unexpectedly changed: {token}')
    return updated, list(missing)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true', help='Write recovered declarations to the manifest')
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[1])
    arguments = parser.parse_args()
    repo = arguments.repo.resolve()
    path = repo / MANIFEST_PATH
    current = path.read_text(encoding='utf-8')
    names = set(provider_map(current))
    revision, providers = most_recent_history_providers(repo, names)
    updated, restored = append_missing_providers(current, providers, repo)
    if not restored:
        print('No missing providers; no changes made.')
        return 0
    print(f'Recovered {len(restored)} provider(s) from git revision {revision[:12]}:')
    for name in restored:
        print('  +', name)
    if arguments.apply:
        path.write_text(updated, encoding='utf-8')
        print('Updated:', path)
    else:
        print('Dry run only: re-run with --apply to write manifest')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (OSError, RuntimeError, ValueError, ET.ParseError) as error:
        print('ERROR:', error, file=sys.stderr)
        sys.exit(1)