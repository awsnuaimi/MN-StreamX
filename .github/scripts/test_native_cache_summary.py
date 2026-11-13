"""Behavioral tests for the optional ccache report. All input is synthetic."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from native_cache_summary import parse_stats, render, restore_state

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / '.github/scripts/native_cache_summary.py'
WORKFLOW = ROOT / '.github/workflows/build.yml'


class NativeCacheSummaryTests(unittest.TestCase):
    def test_modern_ccache_counters(self):
        result = parse_stats('''Cacheable calls:   300 / 310 (96.77%)
  Hits:            240 / 300 (80.0%)
    Direct:        195 / 240
    Preprocessed:  45 / 240
  Misses:          60 / 300
Uncacheable calls: 10 / 310
''')
        self.assertEqual(result, {'cacheable': 300, 'hits': 240, 'misses': 60})

    def test_older_ccache_counters(self):
        result = parse_stats('''cache hit (direct)                  180
cache hit (preprocessed)            14
cache miss                         106
files in cache                    7000
''')
        self.assertEqual(result, {'cacheable': None, 'hits': 194, 'misses': 106})

    def test_reports_real_hit_rate_without_exposing_key(self):
        result = render('Hits: 9 / 12\nMisses: 3 / 12\n', 'false', 'opaque-cache-key', '1')
        self.assertIn('75.0%', result)
        self.assertIn('Compatible earlier cache snapshot restored', result)
        self.assertNotIn('opaque-cache-key', result)

    def test_zero_calls_are_not_reported_as_full_cache_hits(self):
        result = render('Hits: 0\nMisses: 0\n', 'true', 'key', '1')
        self.assertIn('Hit rate: not measurable', result)
        self.assertIn('Exact cache snapshot restored', result)

    def test_missing_stats_not_a_false_cache_success(self):
        report = render('', '', '', '1')
        self.assertIn('Hits: not reported', report)
        self.assertIn('No restored cache snapshot', report)
        self.assertNotIn('100.0%', report)

    def test_disabled_launcher_does_not_imply_cached_compiles(self):
        result = render('Hits: 3\nMisses: 1\n', 'true', 'key', '0')
        self.assertIn('Compiler launcher enabled: no', result)
        self.assertIn('Hit rate: not measurable', result)

    def test_restore_state_variants(self):
        self.assertIn('Exact', restore_state('true', ''))
        self.assertIn('earlier', restore_state('false', 'key'))
        self.assertIn('No restored', restore_state('false', ''))

    def test_cli_writes_artifact_and_github_summary_without_raw_values(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root/'stats.txt').write_text('Cacheable calls: 20\nHits: 8\nMisses: 12\n')
            report = root/'cache-summary.txt'
            step = root/'step.md'
            run = subprocess.run([sys.executable, str(SCRIPT),
                '--stats', str(root/'stats.txt'), '--output', str(report),
                '--step-summary', str(step), '--restore-hit', 'false',
                '--matched-key', 'private-opaque-match', '--enabled', '1'],
                check=False, capture_output=True, text=True)
            self.assertEqual(0, run.returncode, run.stderr)
            self.assertIn('40.0%', report.read_text())
            self.assertIn('Native C/C++ cache', step.read_text())
            self.assertNotIn('private-opaque-match', step.read_text())

    def test_workflow_retains_release_signature_and_avoids_preflight(self):
        source = WORKFLOW.read_text()
        self.assertNotIn('profile_gradle.sh manifest', source)
        self.assertIn('profile_gradle.sh release assembleRelease', source)
        self.assertIn('MN_STREAMX_KEYSTORE_BASE64', source)
        self.assertIn('for abi in arm64-v8a armeabi-v7a x86_64', source)
        self.assertIn('native-cache-summary.txt', source)
        self.assertIn('native_cache_summary.py', source)
        self.assertIn('actions/cache/restore@v4', source)
        self.assertIn('actions/cache/save@v4', source)
        self.assertIn('gradle/actions/setup-gradle@v4', source)
        self.assertNotIn('--init-script', source)


if __name__ == '__main__':
    unittest.main()