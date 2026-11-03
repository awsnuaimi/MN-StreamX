#!/usr/bin/env python3
"""Run device benchmarks and collect AndroidX JSON from the selected TV."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

PACKAGE = "com.yourapp.iptv"


def execute(args, *, check=True, capture=True, env=None):
    return subprocess.run(args, check=check, text=True, capture_output=capture, env=env)


def choose_device(adb, serial):
    result = execute([adb, "devices", "-l"])
    connected = []
    for line in result.stdout.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            connected.append(parts[0])
    if serial:
        if serial not in connected:
            raise RuntimeError(f"Selected device {serial!r} is not connected and authorized")
        return serial
    if len(connected) != 1:
        raise RuntimeError("Connect exactly one authorized device or pass --serial")
    return connected[0]


def locate_json(output):
    candidates = []
    for file in output.rglob("*.json"):
        try:
            data = json.loads(file.read_text(encoding="utf-8"))
        except (ValueError, OSError, UnicodeError):
            continue
        if isinstance(data, dict) and isinstance(data.get("benchmarks"), list) and data["benchmarks"]:
            candidates.append(file)
    if not candidates:
        raise RuntimeError("No AndroidX benchmark JSON found. Inspect test output and device logs.")
    return max(candidates, key=lambda path: (path.stat().st_mtime_ns, str(path)))


def run(args):
    serial = choose_device(args.adb, args.serial)
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    env["ANDROID_SERIAL"] = serial
    print(f"Target device: {serial}", flush=True)
    # Android Gradle Plugin retrieves instrumentation output into build/outputs/connected_android_test_additional_output.
    command = [args.gradle, "--no-daemon", ":app:connectedBenchmarkAndroidTest"]
    source_dir = Path("app/build/outputs/connected_android_test_additional_output")
    # Remove old captures so a successful run cannot accidentally report stale results.
    from shutil import rmtree
    if source_dir.exists():
        rmtree(source_dir)
    execute(command, capture=False, env=env)
    if not source_dir.exists():
        raise RuntimeError(f"No device output directory: {source_dir}. Check instrumentation results.")
    found = locate_json(source_dir)
    from shutil import copy2
    destination = output / "benchmark-results.json"
    copy2(found, destination)
    print(f"Captured: {destination} (from {found})")
    execute([sys.executable, str(Path(__file__).with_name("benchmark_report.py")),
             str(destination), "--output", str(output / "benchmark-report.md")], capture=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", help="ADB device serial; required when multiple devices are connected")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--gradle", default="gradle")
    parser.add_argument("--output", type=Path, default=Path("benchmark-results"))
    args = parser.parse_args()
    try:
        run(args)
    except (RuntimeError, subprocess.CalledProcessError, OSError) as exc:
        parser.exit(1, f"Benchmark collection failed: {exc}\n")


if __name__ == "__main__":
    main()