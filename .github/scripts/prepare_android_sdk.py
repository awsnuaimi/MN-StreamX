#!/usr/bin/env python3
"""Prepare the project's pinned native SDK packages before Gradle starts."""

import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile


VERSION = re.compile(r"[0-9]+(?:\.[0-9]+){2}")


def packages(project):
    source = (project / 'app/build.gradle').read_text(encoding='utf-8')
    patterns = {
        'ndk': r'''\bndkVersion\s+["']([^"']+)["']''',
        'cmake': r'''\bcmake\s*\{[^}]*\bversion\s+["']([^"']+)["']''',
    }
    result = []
    for name, pattern in patterns.items():
        match = re.search(pattern, source)
        if match is None or not VERSION.fullmatch(match[1]):
            raise RuntimeError(f'Cannot read the pinned {name} version from app/build.gradle')
        result.append((name, match[1]))
    return result


def ready(sdk, package):
    name, version = package
    folder = sdk / name / version
    properties = folder / 'source.properties'
    if not properties.is_file():
        return False
    revision = re.search(r'^Pkg.Revision\s*=\s*(\S+)',
                         properties.read_text(encoding='utf-8'), re.MULTILINE)
    if revision is None or revision[1] != version:
        return False
    if name == 'ndk':
        executable = folder / 'toolchains/llvm/prebuilt/linux-x86_64/bin/clang'
        files = [folder / 'ndk-build', folder / 'build/cmake/android.toolchain.cmake']
    else:
        executable = folder / 'bin/cmake'
        files = [folder / 'bin/ninja']
    return executable.is_file() and os.access(executable, os.X_OK) and all(path.is_file() for path in files)


def report_space(paths):
    for path in dict.fromkeys(path.resolve() for path in paths):
        usage = shutil.disk_usage(path)
        inode = os.statvfs(path)
        print(f'{path}: {usage.free // (1024 ** 2)} MiB free, '
              f'{inode.f_favail} free inodes', flush=True)


def prune_unused_ndks(sdk, required):
    # This job uses one pinned NDK. Reclaim other versions on disposable hosted runners.
    if os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
        return
    directory = sdk / 'ndk'
    if not directory.is_dir() or directory.is_symlink():
        return
    for candidate in directory.iterdir():
        if (candidate.name != required and VERSION.fullmatch(candidate.name)
                and candidate.is_dir() and not candidate.is_symlink()):
            print(f'Removing unused hosted-runner NDK {candidate.name}', flush=True)
            shutil.rmtree(candidate)


def prepare(project, sdk, temporary, attempts=3):
    project = project.resolve()
    sdk = sdk.resolve()
    temporary = temporary.resolve()
    if sdk == Path('/') or not (sdk / 'cmdline-tools').is_dir():
        raise RuntimeError('ANDROID_HOME must identify an installed Android SDK')
    wanted = packages(project)
    sdkmanager = sdk / 'cmdline-tools/latest/bin/sdkmanager'
    if not sdkmanager.is_file():
        discovered = shutil.which('sdkmanager')
        if discovered is None:
            raise RuntimeError('sdkmanager is unavailable')
        sdkmanager = Path(discovered)
    temporary.mkdir(parents=True, exist_ok=True)
    prune_unused_ndks(sdk, dict(wanted)['ndk'])
    for directory in ('build/ci-metrics', 'build/reports/profile', 'app/build/reports/profile'):
        destination = project / directory
        destination.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryFile(dir=destination) as probe:
            probe.write(b'MN StreamX report directory check\n')
            probe.flush()
    report_space([project, sdk, temporary])
    missing = [package for package in wanted if not ready(sdk, package)]
    if not missing:
        print('Pinned NDK and CMake are already installed.', flush=True)
        return
    requested = [f'{name};{version}' for name, version in missing]
    print('Preparing ' + ', '.join(requested), flush=True)
    for attempt in range(1, attempts + 1):
        print(f'Native SDK installation attempt {attempt}/{attempts}', flush=True)
        # A fresh SDK root and download cache prevent reuse of a corrupt archive.
        with tempfile.TemporaryDirectory(prefix='mnx-native-sdk-', dir=temporary) as directory:
            work = Path(directory)
            staging = work / 'sdk'
            staging.mkdir()
            if (sdk / 'licenses').is_dir():
                shutil.copytree(sdk / 'licenses', staging / 'licenses')
            environment = os.environ.copy()
            environment['ANDROID_USER_HOME'] = str(work / 'android-user')
            environment['ANDROID_PREFS_ROOT'] = str(work / 'android-preferences')
            try:
                result = subprocess.run(
                    [str(sdkmanager), f'--sdk_root={staging}', '--install', *requested],
                    env=environment, stdin=subprocess.DEVNULL, timeout=600, check=False,
                )
                installed = result.returncode == 0 and all(ready(staging, package) for package in missing)
            except subprocess.TimeoutExpired:
                print('::warning::Native SDK download exceeded the installation timeout.', flush=True)
                installed = False
            if installed:
                for name, version in missing:
                    destination = sdk / name / version
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    if destination.is_symlink():
                        destination.unlink()
                    elif destination.exists():
                        shutil.rmtree(destination)
                    shutil.move(str(staging / name / version), destination)
                if not all(ready(sdk, package) for package in wanted):
                    raise RuntimeError('Native SDK validation failed after installation')
                report_space([project, sdk, temporary])
                print('Pinned native SDK packages are ready.', flush=True)
                return
        print('::warning::Native SDK installation failed; discarded this attempt.', flush=True)
        report_space([project, sdk, temporary])
    raise RuntimeError(f'Could not install {", ".join(requested)} after {attempts} clean attempts')


def main():
    project = Path(__file__).resolve().parents[2]
    sdk_root = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk_root:
        raise RuntimeError('ANDROID_HOME or ANDROID_SDK_ROOT must be set')
    temporary = Path(os.environ.get('RUNNER_TEMP') or tempfile.gettempdir())
    sdk = Path(sdk_root).resolve()
    prepare(project, sdk, temporary)
    environment_file = os.environ.get('GITHUB_ENV')
    if environment_file:
        ndk = sdk / 'ndk' / dict(packages(project))['ndk']
        with Path(environment_file).open('a', encoding='utf-8') as stream:
            for variable in ('ANDROID_NDK', 'ANDROID_NDK_HOME', 'ANDROID_NDK_ROOT'):
                stream.write(f'{variable}={ndk}\n')


if __name__ == '__main__':
    try:
        main()
    except (OSError, RuntimeError) as error:
        print(f'::error::{error}', file=sys.stderr)
        sys.exit(1)