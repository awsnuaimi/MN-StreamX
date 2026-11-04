# MN StreamX — Android TV Benchmark Guide (v35.0.46.7)

## What these measurements mean

The eight AndroidX microbenchmarks use synthetic VOD data to time filtering, incremental search, focus-key lookup, category switching, and compressed cache I/O. They do **not** measure real remote-control latency, frame rendering, app startup, playback, or memory consumption. GitHub Actions compiles the instrumentation tests but cannot run them without an attached device.

## Requirements

- A computer with Python 3, JDK 17, Android SDK, `adb`, and Gradle 8.7 installed.
- An Android TV device with developer options and USB or network debugging enabled.
- The project checked out locally; this repository has no `gradlew` wrapper.
- An authorized device visible to `adb`. No IPTV credentials are required.

## 1. Connect and authorize the device

Connect by USB or pair and connect via the TV's network debugging settings. Check that the state is `device`, not `offline` or `unauthorized`:

```sh
adb devices -l