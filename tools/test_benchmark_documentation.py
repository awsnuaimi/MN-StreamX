
**الحالة:** 🔄 **استبدال** — تحديث ملف موجود (من v35.0.46.6)

**التغييرات الجوهرية:**
- ✅ `VERSION` محدّث إلى `v35.0.46.7`
- ✅ `test_exactly_one_versioned_benchmark_guide`: المقارنة أصبحت بأسماء الملفات (`.name`) بدل كائنات `Path` — أوضح في رسائل الفشل
- ✅ `test_guide_has_matching_title_and_complete_commands`: 
  - يتحقق من `({VERSION})` **بين قوسين** (أدق من `VERSION` المجرد)
  - قائمة الرموز المطلوبة تغيّرت: أُضيف `connectedBenchmarkAndroidTest`، `--serial`، `--threshold-percent`، `Troubleshooting` | حُذف `:app:compileBenchmarkAndroidTestKotlin`، `python3 -m unittest discover -s tools`
  - المعيار تغيّر من `content.count("```") >= 12` إلى `len(guide.splitlines()) > 65` (تحقق من حجم الملف بدل عدد الكتل)
  - `assertEqual(..., 0, "Unclosed Markdown code fence")` — أصبح له **رسالة فشل** توضيحية
- 🆕 **اختبار جديد:** `test_no_old_guide_references_in_current_docs` — يتحقق من خلو `README.md` و`README-AR.md` من أي مرجع لإصدار قديم `v35.0.46.[0-6]`
- 📊 المجموع: **3 اختبارات** (كانت 2)

```python
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