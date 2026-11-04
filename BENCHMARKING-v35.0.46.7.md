# MN StreamX — Android TV Benchmarking (v35.0.46.7)

This guide describes running the eight AndroidX microbenchmarks on a real Android TV device.
These measurements are not UI frame-time, playback, or startup benchmarks.

## Prerequisites

- Python 3, JDK 17, Android SDK with ADB, and Gradle available on PATH.
- The repository has no Gradle wrapper (`gradlew`); install a compatible Gradle version.
- Enable developer options and debugging on the Android TV device.
- Authorize the debugging connection on the device.
- Run commands from the project root unless explicitly noted.

## 1. Connect the device

For a USB-connected TV, authorize the debugging prompt.
For network debugging, use your device's pairing or connection procedure.

    adb devices -l

The target must be listed as `device`, not `unauthorized` or `offline`.
If several devices are connected, select one explicitly with `--serial`.

## 2. Run the eight benchmarks

The runner invokes the Gradle task `connectedBenchmarkAndroidTest` and validates
that the complete eight-benchmark suite produced timing measurements.

    python tools/run_device_benchmarks.py --output benchmark-results

When multiple devices are attached, specify the device identifier:

    python tools/run_device_benchmarks.py --serial TV1 --output benchmark-results

Replace `TV1` with the serial displayed by `adb devices -l`.
To override executable paths, use `--adb` and `--gradle`.
The runner executes Gradle from the repository root even when invoked elsewhere.

## 3. Inspect the results

After successful collection, the output directory contains:

- `benchmark-results.json`: captured AndroidX benchmark measurements.
- `benchmark-report.md`: readable median timings in milliseconds.

To regenerate the report from an existing capture:

    python tools/benchmark_report.py benchmark-results/benchmark-results.json --output benchmark-results/benchmark-report.md

The report requires valid `timeNs` measurements in the JSON payload.
A successful CI build alone does not provide real-device timing measurements.

## 4. Compare a baseline and a candidate

Keep baseline and candidate captures from the same TV, under similar conditions.
Store the baseline separately before rerunning the suite.

    python tools/compare_benchmarks.py baseline/benchmark-results.json benchmark-results/benchmark-results.json --threshold-percent 10 --output benchmark-comparison.md

The comparison tool exits nonzero for regressions, missing measurements, or invalid comparisons.
The threshold is a screening value; investigate repeatability before drawing conclusions.

## 5. Run documentation and tooling tests

    python -m unittest discover -s tools -p 'test_*.py' -v

This verifies the Python tooling and guide structure, not Android TV runtime performance.

## Troubleshooting

### ADB reports unauthorized or offline

Reauthorize the device, reconnect it, and run `adb devices -l` again.
Specify `--serial` if more than one authorized device is attached.

### Gradle executable not found

Install a compatible Gradle version and check your PATH, or provide `--gradle`.
The repository does not include a `gradlew` script.

### connectedBenchmarkAndroidTest fails

Check the connected device, benchmark build variant, Android SDK, and instrumentation output.
Read the full Gradle failure before changing application code.

### No benchmark-results.json appears

Inspect `app/build/outputs/connected_android_test_additional_output`.
The runner deliberately rejects absent, stale, ambiguous, or incomplete captures.

### A comparison flags a regression

Repeat both captures on the same device under similar temperature and load.
Treat differences as evidence for investigation, not proof of a visible playback slowdown.