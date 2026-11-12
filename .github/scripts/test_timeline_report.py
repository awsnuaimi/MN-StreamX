import tempfile
import unittest
from pathlib import Path

from timeline_report import Interval, interval_union_duration, read_timeline, render_report


class TimelineReportTests(unittest.TestCase):
    def test_union_counts_concurrent_intervals_once(self):
        self.assertEqual(interval_union_duration([(0, 5), (3, 10), (15, 20)]), 15)
        self.assertEqual(interval_union_duration([]), 0)

    def test_manifest_overlap_is_bounded_by_manifest_duration(self):
        rows = [
            Interval(":app:processReleaseManifestForPackage", 1000, 13000, 12000, "COMPLETED"),
            Interval(":app:buildCMakeRelWithDebInfo[arm64-v8a]", 2000, 9000, 7000, "COMPLETED"),
            Interval(":app:buildCMakeRelWithDebInfo[x86_64]", 7000, 16000, 9000, "COMPLETED"),
        ]
        report = render_report(rows)
        self.assertIn("union overlap with Manifest: 11.0s", report)
        self.assertIn("overlap does not establish causality", report)

    def test_tsv_read_and_invalid_record(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "timeline.tsv"
            path.write_text("phase\ttask\tstart_epoch_ms\tend_epoch_ms\telapsed_ms\tstatus\n"
                            "release\t:app:processReleaseManifestForPackage\t100\t200\t100\tCOMPLETED\n",
                            encoding="utf-8")
            self.assertEqual(read_timeline(path)[0].elapsed, 100)
            path.write_text("phase\ttask\tstart_epoch_ms\tend_epoch_ms\telapsed_ms\tstatus\n"
                            "release\t:app:a\t200\t100\t100\tCOMPLETED\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                read_timeline(path)

    def test_missing_file_is_nonfatal(self):
        self.assertEqual(read_timeline(Path("nonexistent-task-timeline.tsv")), [])
        self.assertIn("No release task timeline", render_report([]))


if __name__ == "__main__":
    unittest.main()
