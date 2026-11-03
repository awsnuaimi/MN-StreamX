# MN StreamX — Android TV Benchmark Guide (v35.0.46.5)

## Purpose

The eight AndroidX microbenchmarks measure synthetic VOD filtering, incremental search, focus bookmark resolution, category switching and compressed cache I/O. They do **not** measure UI frame pacing, actual remote-control latency, startup time, playback quality or memory consumption. GitHub Actions compiles the benchmark tests but does not run them on a television.

## Requirements

- A computer with Python 3, Java 17, Android SDK/ADB, and Gradle 8.7 available as `gradle` (the repository does not include `gradlew`).
- An Android TV device with developer options and USB/network debugging enabled and authorized.
- Use the same device and comparable thermal conditions when comparing versions. No IPTV account or real content is required.

## Connect the TV

```sh
adb devices -l