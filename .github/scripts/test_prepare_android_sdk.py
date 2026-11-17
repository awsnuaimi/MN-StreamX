"""Exercise native SDK preparation without downloading or modifying a real SDK."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import prepare_android_sdk as sdk_tools


class PrepareAndroidSdkTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.project = self.root / 'project'
        (self.project / 'app').mkdir(parents=True)
        (self.project / 'app/build.gradle').write_text(
            'android { ndkVersion "27.2.12479018"\n'
            'externalNativeBuild { cmake { version "3.22.1" } } }\n'
        )
        self.sdk = self.root / 'sdk'
        tools = self.sdk / 'cmdline-tools/latest/bin'
        tools.mkdir(parents=True)
        (tools / 'sdkmanager').touch()
        (self.sdk / 'licenses').mkdir()
        (self.sdk / 'licenses/android-sdk-license').write_text('existing license\n')
        self.work = self.root / 'temporary'
        self.packages = sdk_tools.packages(self.project)
        environment = patch.dict(os.environ, {'RUNNER_ENVIRONMENT': 'self-hosted'})
        environment.start()
        self.addCleanup(environment.stop)

    def populate(self, root, package):
        name, version = package
        folder = root / name / version
        folder.mkdir(parents=True, exist_ok=True)
        (folder / 'source.properties').write_text(f'Pkg.Revision = {version}\n')
        files = (['ndk-build', 'build/cmake/android.toolchain.cmake',
                  'toolchains/llvm/prebuilt/linux-x86_64/bin/clang']
                 if name == 'ndk' else ['bin/cmake', 'bin/ninja'])
        for name in files:
            target = folder / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text('#!/bin/sh\nexit 0\n')
            target.chmod(0o755)

    def prepare(self):
        sdk_tools.prepare(self.project, self.sdk, self.work)

    def test_pins_come_from_the_build_configuration(self):
        self.assertEqual([('ndk', '27.2.12479018'), ('cmake', '3.22.1')], self.packages)

    def test_healthy_packages_are_reused_without_a_download(self):
        for package in self.packages:
            self.populate(self.sdk, package)
        with patch.object(sdk_tools.subprocess, 'run') as installer:
            self.prepare()
        installer.assert_not_called()
        self.assertTrue((self.project / 'build/reports/profile').is_dir())
        self.assertTrue((self.project / 'app/build/reports/profile').is_dir())

    def test_failed_archive_gets_a_fresh_root_and_cache_on_retry(self):
        roots, caches = [], []
        broken = self.sdk / 'ndk/27.2.12479018/broken-download'
        broken.parent.mkdir(parents=True)
        broken.touch()

        def install(command, **kwargs):
            staging = Path(command[1].split('=', 1)[1])
            roots.append(staging)
            caches.append(kwargs['env']['ANDROID_USER_HOME'])
            self.assertEqual(command[2:], ['--install', 'ndk;27.2.12479018', 'cmake;3.22.1'])
            self.assertEqual('existing license\n', (staging / 'licenses/android-sdk-license').read_text())
            self.assertTrue(broken.exists())
            if len(roots) == 1:
                (staging / 'corrupt.zip').write_text('invalid download')
                return subprocess.CompletedProcess(command, 1)
            self.assertFalse(roots[0].exists())
            for package in self.packages:
                self.populate(staging, package)
            return subprocess.CompletedProcess(command, 0)

        with patch.object(sdk_tools.subprocess, 'run', side_effect=install) as installer:
            self.prepare()
        self.assertEqual(2, installer.call_count)
        self.assertEqual(2, len(set(caches)))
        self.assertTrue(all(not path.exists() for path in roots))
        self.assertTrue(all(sdk_tools.ready(self.sdk, package) for package in self.packages))
        self.assertFalse(broken.exists())

    def test_success_exit_with_incomplete_files_is_retried(self):
        with patch.object(sdk_tools.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0)) as installer:
            with self.assertRaisesRegex(RuntimeError, 'after 3 clean attempts'):
                self.prepare()
        self.assertEqual(3, installer.call_count)
        self.assertEqual([], list(self.work.iterdir()))

    def test_timeout_is_retried_and_every_staging_directory_is_removed(self):
        with patch.object(sdk_tools.subprocess, 'run', side_effect=subprocess.TimeoutExpired('sdkmanager', 600)) as installer:
            with self.assertRaisesRegex(RuntimeError, 'after 3 clean attempts'):
                self.prepare()
        self.assertEqual(3, installer.call_count)
        self.assertEqual([], list(self.work.iterdir()))

    def test_failed_install_preserves_existing_packages(self):
        self.populate(self.sdk, self.packages[1])
        with patch.object(sdk_tools.subprocess, 'run', return_value=subprocess.CompletedProcess([], 1)) as installer:
            with self.assertRaisesRegex(RuntimeError, 'Could not install ndk;27.2.12479018'):
                self.prepare()
        self.assertTrue(sdk_tools.ready(self.sdk, self.packages[1]))
        self.assertEqual(['--install', 'ndk;27.2.12479018'], installer.call_args.args[0][2:])

    def test_hosted_cleanup_keeps_pinned_packages_and_ignores_symlinks(self):
        ndks = self.sdk / 'ndk'
        pinned = ndks / '27.2.12479018'
        pinned.mkdir(parents=True)
        unused = ndks / '29.0.12345678'
        unused.mkdir()
        outside = self.root / 'outside'
        outside.mkdir()
        (ndks / '28.0.12345678').symlink_to(outside, target_is_directory=True)
        (ndks / '30.0.12345678').touch()
        with patch.dict(os.environ, {'RUNNER_ENVIRONMENT': 'github-hosted'}):
            sdk_tools.prune_unused_ndks(self.sdk, '27.2.12479018')
        self.assertTrue(pinned.is_dir())
        self.assertFalse(unused.exists())
        self.assertTrue(outside.is_dir())
        self.assertTrue((ndks / '28.0.12345678').is_symlink())
        self.assertTrue((ndks / '30.0.12345678').is_file())

    def test_self_hosted_sdk_is_not_pruned(self):
        unused = self.sdk / 'ndk/29.0.12345678'
        unused.mkdir(parents=True)
        sdk_tools.prune_unused_ndks(self.sdk, '27.2.12479018')
        self.assertTrue(unused.is_dir())

    def test_main_exports_the_verified_ndk_for_following_steps(self):
        for package in self.packages:
            self.populate(self.sdk, package)
        environment_file = self.root / 'github-environment'
        environment = {
            'ANDROID_HOME': str(self.sdk),
            'RUNNER_TEMP': str(self.work),
            'GITHUB_ENV': str(environment_file),
        }
        with patch.object(sdk_tools, '__file__', str(self.project / '.github/scripts/prepare_android_sdk.py')):
            with patch.dict(os.environ, environment):
                sdk_tools.main()
        ndk = self.sdk / 'ndk/27.2.12479018'
        self.assertEqual(
            [f'{name}={ndk}' for name in ('ANDROID_NDK', 'ANDROID_NDK_HOME', 'ANDROID_NDK_ROOT')],
            environment_file.read_text().splitlines(),
        )


if __name__ == '__main__':
    unittest.main()