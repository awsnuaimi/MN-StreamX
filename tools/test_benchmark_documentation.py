"""Prevent incomplete or mismatched Android TV benchmark documentation."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parent.parent
VERSION = "v35.0.46.7"


class BenchmarkDocumentationTests(unittest.TestCase):
    def test_exactly_one_versioned_benchmark_guide(self):
        guides = sorted(ROOT.glob("BENCHMARKING-v*.md"))
        self.assertEqual([path.name for path in guides], [f"BENCHMARKING-{VERSION}.md"])

    def test_guide_has_matching_title_and_complete_commands(self):
        guide = (ROOT / f"BENCHMARKING-{VERSION}.md").read_text(encoding="utf-8")
        self.assertIn(f"({VERSION})", guide.splitlines()[0])
        self.assertEqual(guide.count("```" ) % 2, 0, "Unclosed Markdown code fence")
        required = (
            "adb devices -l",
            "tools/run_device_benchmarks.py",
            "tools/benchmark_report.py",
            "tools/compare_benchmarks.py",
            "benchmark-results.json",
            "connectedBenchmarkAndroidTest",
            "--serial",
            "--threshold-percent",
            "Troubleshooting",
        )
        for token in required:
            with self.subTest(token=token):
                self.assertIn(token, guide)
        self.assertGreater(len(guide.splitlines()), 65, "Guide appears truncated")

    def test_no_old_guide_references_in_current_docs(self):
        old_name = re.compile(r"BENCHMARKING-v35\.0\.46\.[0-6]\.md")
        for file in (ROOT / "README.md", ROOT / "README-AR.md"):
            if file.exists():
                with self.subTest(file=file.name):
                    self.assertIsNone(old_name.search(file.read_text(encoding="utf-8")))


if __name__ == "__main__":
    unittest.main()