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
            (folder / "results.json").write_text('{"benchmarks": []}')
            self.assertEqual(runner.locate_json(folder).name, "results.json")

    def test_rejects_missing_results(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(RuntimeError, "No AndroidX"):
                runner.locate_json(Path(tmp))


if __name__ == "__main__":
    unittest.main()