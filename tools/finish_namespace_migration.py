"""Remove unchanged legacy Kotlin files only after every renamed replacement is verified."""
import argparse
import hashlib
import json
from pathlib import Path

OLD = 'com/yourapp/iptv'
NEW = 'mnstudio/apps/mnstreamx'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def apply_migration(root, entries, apply=False):
    root = Path(root).resolve()
    if "namespace 'mnstudio.apps.mnstreamx'" not in (root / 'app/build.gradle').read_text():
        raise ValueError('The new app namespace must be installed before migration.')
    pending = []
    seen = set()
    for entry in entries:
        old = entry['old']
        expected = None
        for source in ('main', 'test', 'androidTest'):
            prefix = f'app/src/{source}/java/{OLD}/'
            if old.startswith(prefix) and old.endswith('.kt'):
                expected = old.replace(f'/java/{OLD}/', f'/java/{NEW}/', 1)
                break
        if expected is None or entry['new'] != expected or old in seen or '..' in Path(old).parts:
            raise ValueError('Invalid or repeated migration path.')
        seen.add(old)
        source = root / old
        target = root / expected
        if not source.resolve().is_relative_to(root) or not target.resolve().is_relative_to(root):
            raise ValueError('Migration paths must stay inside the project.')
        if not source.exists():
            continue
        if not target.is_file() or digest(target) != entry['new_sha256']:
            raise ValueError(f'Missing or modified replacement: {expected}')
        if source.exists():
            if not source.is_file() or digest(source) != entry['old_sha256']:
                raise ValueError(f'Legacy file has local edits; merge them first: {old}')
            pending.append(source)
    # Complete validation precedes every deletion. No user edits are discarded.
    if apply:
        for source in pending:
            source.unlink()
    return len(pending)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    try:
        entries = json.loads((root / 'tools/namespace-migration.json').read_text())
        count = apply_migration(root, entries, args.apply)
    except (OSError, ValueError, KeyError, TypeError) as error:
        parser.exit(1, f'Namespace migration stopped: {error}\n')
    print(f'Legacy Kotlin files {"removed" if args.apply else "verified"}: {count}')


if __name__ == '__main__':
    main()