"""Regression tests for the opt-in CMake compiler launcher (no Android SDK)."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
CACHE_CMAKE = ROOT / 'app/src/main/cpp/NativeCompilerCache.cmake'
WORKFLOW = ROOT / '.github/workflows/build.yml'


class NativeCompilerCacheTest(unittest.TestCase):
    def _configure_and_build(self, enabled: bool):
        with tempfile.TemporaryDirectory() as directory:
            parent = Path(directory)
            source = parent / 'source'
            source.mkdir()
            bin_directory = parent / 'bin'
            bin_directory.mkdir()
            build_dir = parent / 'build'
            log = parent / 'launcher.log'
            ccache = bin_directory / 'ccache'
            ccache.write_text('#!/bin/sh\nprintf "%s\\n" "$*" >> "$MNX_LAUNCH_LOG"\nexec "$@"\n')
            ccache.chmod(0o755)
            (source / 'main.c').write_text('int add(int a, int b) { return a + b; }\n')
            (source / 'main.cpp').write_text('int mult(int a, int b) { return a * b; }\n')
            vendor = source / 'vendor'
            vendor.mkdir()
            (vendor / 'vendor.cpp').write_text('int diff(int a, int b) { return a - b; }\n')
            (vendor / 'CMakeLists.txt').write_text('add_library(vendor STATIC vendor.cpp)\n')
            (source / 'CMakeLists.txt').write_text(
                'cmake_minimum_required(VERSION 3.22)\n'
                'project(launcher_probe LANGUAGES C CXX)\n'
                f'include("{CACHE_CMAKE.as_posix()}")\n'
                'add_library(probe STATIC main.c main.cpp)\n'
                'add_subdirectory(vendor)\n'
            )
            env = os.environ.copy()
            env['PATH'] = f'{bin_directory}{os.pathsep}{env["PATH"]}'
            env['MNX_LAUNCH_LOG'] = str(log)
            env['MN_STREAMX_ENABLE_CCACHE'] = '1' if enabled else '0'
            for argv in (
                ['cmake', '-G', 'Ninja', '-S', str(source), '-B', str(build_dir)],
                ['cmake', '--build', str(build_dir), '--parallel', '2'],
            ):
                completed = subprocess.run(argv, env=env, capture_output=True, text=True)
                self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
            calls = log.read_text().splitlines() if log.exists() else []
            return calls

    def test_launcher_wraps_c_and_cpp_when_enabled(self):
        calls = self._configure_and_build(enabled=True)
        self.assertEqual(len(calls), 3)
        self.assertTrue(any('main.c' in call for call in calls))
        self.assertTrue(any('main.cpp' in call for call in calls))
        self.assertTrue(any('vendor.cpp' in call for call in calls))

    def test_launcher_does_not_run_when_opted_out(self):
        self.assertEqual(self._configure_and_build(enabled=False), [])

    def test_job_env_resolves_runner_temp_only_after_runner_starts(self):
        workflow = WORKFLOW.read_text()
        job_env = workflow.split('    env:', 1)[1].split('    steps:', 1)[0]
        self.assertNotIn('${{ runner.', job_env)
        self.assertNotIn('CCACHE_DIR:', job_env)
        self.assertIn('echo "CCACHE_DIR=$RUNNER_TEMP/mnx-native-ccache" >> "$GITHUB_ENV"', workflow)
        self.assertIn('path: ${{ runner.temp }}/mnx-native-ccache', workflow)
        self.assertIn('mkdir -p "$CCACHE_DIR"', workflow)

    def test_workflow_preserves_signing_and_gradle_cache(self):
        text = WORKFLOW.read_text()
        self.assertIn('gradle/actions/setup-gradle@v4', text)
        self.assertIn('actions/cache/restore@v4', text)
        self.assertIn('actions/cache/save@v4', text)
        self.assertIn('MN_STREAMX_ENABLE_CCACHE=1', text)
        self.assertIn('assembleRelease', text)
        self.assertIn('testDebugUnitTest lintDebug', text)
        self.assertIn('for abi in arm64-v8a armeabi-v7a x86_64', text)
        self.assertNotIn('--init-script', text)
        self.assertNotIn('~/.gradle/caches', text)
        self.assertIn('native-ccache-stats.txt', text)


if __name__ == '__main__':
    unittest.main()