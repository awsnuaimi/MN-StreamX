# MN StreamX — Android TV Benchmark Guide (v35.0.46.4)

## Scope

Eight AndroidX microbenchmarks measure synthetic VOD filtering, incremental search, focus bookmark resolution, category switching and compressed cache I/O. These are operation-level timings, **not** UI frame pacing, remote-control latency, startup time, playback quality, or memory usage. GitHub Actions compiles the benchmark tests but does not execute them on a television.

## Requirements

- Computer with Python 3, JDK 17, Android SDK / ADB and Gradle 8.7 available as `gradle` (this repository does not include `gradlew`).
- Android TV with developer options and debugging enabled, authorized via USB or network ADB.
- Use the same device, build and similar thermal conditions for comparisons. No IPTV credentials are required.

## 1. Connect the TV

```sh
adb devices -l