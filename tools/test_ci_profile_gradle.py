"""Regression tests for CI timings. They never run the Android toolchain."""
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / '.github/scripts/profile_gradle.sh'


class GradleProfileScriptTest(unittest.TestCase):
    def _execute(self, phase='verify', exit_code=0):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            bindir = folder / 'bin'
            bindir.mkdir()
            fake = bindir / 'gradle'
            fake.write_text('#!/bin/sh\nprintf "%s\\n" "$@" > "$ARGS_OUT"\nexit "$FAKE_CODE"\n')
            fake.chmod(0o755)
            env = os.environ.copy()
            env.update({
                'PATH': f'{bindir}{os.pathsep}{env.get("PATH", "")}',
                'ARGS_OUT': str(folder / 'args.txt'),
                'FAKE_CODE': str(exit_code),
                'GITHUB_STEP_SUMMARY': str(folder / 'summary.md'),
            })
            cmd = ['bash', str(SCRIPT), phase, 'testDebugUnitTest']
            result = subprocess.run(cmd, cwd=folder, env=env, capture_output=True, text=True)
            info = {
                'result': result,
                'metrics': (folder / 'build/ci-metrics/gradle-steps.tsv').read_text() if (folder / 'build/ci-metrics/gradle-steps.tsv').exists() else '',
                'args': (folder / 'args.txt').read_text() if (folder / 'args.txt').exists() else '',
                'summary': (folder / 'summary.md').read_text() if (folder / 'summary.md').exists() else '',
            }
            return info

    def test_success_generates_profile_and_summary(self):
        r = self._execute()
        self.assertEqual(0, r['result'].returncode, r['result'].stderr)
        self.assertEqual('--no-daemon\n--profile\n--console=plain\ntestDebugUnitTest\n', r['args'])
        self.assertIn('phase\twall_seconds\texit_code', r['metrics'])
        self.assertRegex(r['metrics'], r'verify\t\d+\t0')
        self.assertIn('MN-StreamX-ci-diagnostics', r['summary'])

    def test_failed_gradle_call_preserves_exit_code_and_metrics(self):
        r = self._execute(exit_code=17)
        self.assertEqual(17, r['result'].returncode)
        self.assertRegex(r['metrics'], r'verify\t\d+\t17')
        self.assertIn('exit code: **17**', r['summary'])

    def test_manifest_phase(self):
        r = self._execute(phase='manifest')
        self.assertEqual(0, r['result'].returncode)
        self.assertRegex(r['metrics'], r'manifest\t\d+\t0')
        self.assertIn('exit code: **0**', r['summary'])

    def test_release_phase(self):
        r = self._execute(phase='release')
        self.assertEqual(0, r['result'].returncode)
        self.assertRegex(r['metrics'], r'release\t\d+\t0')

    def test_unknown_phase_rejected_without_invoking_gradle(self):
        r = self._execute(phase='other')
        self.assertEqual(2, r['result'].returncode)
        self.assertEqual('', r['args'])


if __name__ == '__main__':
    unittest.main()