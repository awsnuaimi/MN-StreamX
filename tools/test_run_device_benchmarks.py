"""Offline tests for the Android TV benchmark capture helper."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("runner", Path(__file__).with_name("run_device_benchmarks.py"))
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


class RunnerTests(unittest.TestCase):
    def test_selects_one_authorized_device(self):
        with patch.object(runner, "execute") as execute:
            execute.return_value.stdout = "List of devices attached\nTV123 device product:tv\n"
            self.assertEqual(runner.choose_device("adb", None), "TV123")

    def test_rejects_ambiguous_devices(self):
        with patch.object(runner, "execute") as execute:
            execute.return_value.stdout = "List of devices attached\nTV1 device\nTV2 device\n"
            with self.assertRaisesRegex(RuntimeError, "exactly one"):
                runner.choose_device("adb", None)

    def test_rejects_unauthorized_device(self):
        with patch.object(runner, "execute") as execute:
            execute.return_value.stdout = "List of devices attached\nTV1 unauthorized\n"
            with self.assertRaises(RuntimeError):
                runner.choose_device("adb", "TV1")

    def test_locates_benchmark_json(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            (folder / "unrelated.json").write_text('{"x": 1}')
            (folder / "results.json").write_text('{"benchmarks": [{"name": "sample", "metrics": {"timeNs": {"median": 12345}}}]}')
            self.assertEqual(runner.locate_json(folder).name, "results.json")

    def test_rejects_empty_benchmark_capture(self):
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp) / "empty.json").write_text('{"benchmarks": []}')
            with self.assertRaisesRegex(RuntimeError, "No AndroidX"):
                runner.locate_json(Path(tmp))

    def test_rejects_capture_without_timing_measurements(self):
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp) / "incomplete.json").write_text(
                '{"benchmarks": [{"name": "sample"}]}', encoding="utf-8"
            )
            with self.assertRaisesRegex(RuntimeError, "No AndroidX"):
                runner.locate_json(Path(tmp))

    def test_rejects_multiple_valid_captures(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            payload = '{"benchmarks": [{"name": "sample", "metrics": {"timeNs": {"median": 12345}}}]}'
            (folder / "first.json").write_text(payload, encoding="utf-8")
            (folder / "second.json").write_text(payload, encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "Found 2 benchmark JSON"):
                runner.locate_json(folder)

    def test_rejects_partial_benchmark_suite(self):
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / "partial.json"
            file.write_text(
                '{"benchmarks": [{"name": "fullCategoryAndSearchFilterOnSixtyThousandTitles", '
                '"metrics": {"timeNs": {"median": 100}}}]}', encoding="utf-8"
            )
            with self.assertRaisesRegex(RuntimeError, "missing benchmarks"):
                runner.validate_suite(file)

    def test_accepts_all_eight_benchmarks(self):
        import json
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / "complete.json"
            file.write_text(json.dumps({"benchmarks": [
                {"name": "mnstudio.apps.mnstreamx.performance.VodPerformanceBenchmark." + name,
                 "metrics": {"timeNs": {"median": 100}}}
                for name in sorted(runner.EXPECTED_BENCHMARKS)
            ]}), encoding="utf-8")
            self.assertEqual(len(runner.validate_suite(file)), 8)

    def test_rejects_duplicate_benchmark_names(self):
        import json
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / "duplicate.json"
            names = list(sorted(runner.EXPECTED_BENCHMARKS))
            names.append(names[0])
            file.write_text(json.dumps({"benchmarks": [
                {"name": name, "metrics": {"timeNs": {"median": 100}}}
                for name in names
            ]}), encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "duplicate benchmarks"):
                runner.validate_suite(file)

    def test_runs_gradle_from_project_root_not_callers_directory(self):
        from argparse import Namespace
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "results"
            with patch.object(runner, "choose_device", return_value="TV1"), \
                 patch.object(runner, "execute") as execute, \
                 patch.object(runner, "locate_json", side_effect=RuntimeError("stop")):
                # A capture directory is needed to reach the locator.
                source = Path(runner.__file__).resolve().parent.parent / \
                    "app/build/outputs/connected_android_test_additional_output"
                with patch.object(Path, "exists", autospec=True, return_value=True), \
                     patch("shutil.rmtree"):
                    with self.assertRaisesRegex(RuntimeError, "stop"):
                        runner.run(Namespace(adb="adb", serial=None, gradle="gradle", output=output))
                self.assertEqual(execute.call_args.kwargs["cwd"],
                                 Path(runner.__file__).resolve().parent.parent)

    def test_rejects_missing_results(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(RuntimeError, "No AndroidX"):
                runner.locate_json(Path(tmp))


if __name__ == "__main__":
    unittest.main()