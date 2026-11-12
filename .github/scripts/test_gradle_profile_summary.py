"""Synthetic regression tests; no Android SDK or real accounts required."""
from pathlib import Path
import tempfile
import unittest
from gradle_profile_summary import seconds, metrics_rows, profile, render


class GradleSummaryTest(unittest.TestCase):
    def test_duration_parsing(self):
        self.assertAlmostEqual(seconds('16m3.35s'), 963.35)
        self.assertAlmostEqual(seconds('1h2m3s'), 3723)
        with self.assertRaises(ValueError):
            seconds('unknown')

    def test_profile_extracts_app_tasks(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'profile.html'
            p.write_text('''<html><h1>Profile report</h1><table><tr>
            <td>Profiled build:</td><td>assembleRelease</td></tr>
            <tr><td>:app:processReleaseManifestForPackage</td><td>16m3.35s</td><td></td></tr>
            <tr><td>:app:buildCMake[x86_64]</td><td>5m2s</td></tr>
            </table></html>''')
            cmd, top = profile(p)
            self.assertEqual(cmd, 'assembleRelease')
            self.assertEqual(top[0], (':app:processReleaseManifestForPackage', 963.35))
            self.assertIn('can overlap', render([p], []))

    def test_profile_missing_is_safe(self):
        self.assertIn('No Gradle HTML profiles found.', render([], []))
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(metrics_rows(Path(d) / 'notfound.tsv'), [])

    def test_metrics_are_restricted_and_parsed(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'metrics.tsv'
            p.write_text('phase\twall_seconds\texit_code\nverify\t100\t0\nmanifest\t12\t0\n'
                         'release\t421\t1\nother\t12\t0\n', encoding='utf8')
            self.assertEqual(metrics_rows(p), [
                ('verify', 100, 0), ('manifest', 12, 0), ('release', 421, 1)])

    def test_includes_no_secrets_or_external_files(self):
        with tempfile.TemporaryDirectory() as d:
            file = Path(d) / 'profile.html'
            file.write_text('<html><table><tr><td>:app:compileReleaseKotlin</td><td>20s</td></tr></table></html>')
            result = render([file], [('release', 500, 0)])
            self.assertIn('compileReleaseKotlin', result)
            self.assertNotIn('MN_STREAMX_KEYSTORE_PASSWORD', result)

if __name__ == '__main__':
    unittest.main()