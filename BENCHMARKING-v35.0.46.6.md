# MN StreamX — Android TV Benchmark Guide (v35.0.46.6)

## Scope

The eight AndroidX microbenchmarks exercise synthetic VOD filtering, incremental search, focus bookmark lookup, category changes, and compressed cache I/O. They do **not** measure actual remote-control latency, UI frame pacing, playback, startup time, or memory usage. GitHub Actions compiles benchmark tests; it does not execute them on an Android TV device.

## Prerequisites

- A computer with Python 3, JDK 17, Android SDK/ADB, and Gradle 8.7 on PATH (the repository does not include `gradlew`).
- An Android TV device with developer options and USB/network debugging enabled.
- A USB connection or authorized ADB-over-network connection. No IPTV account is needed.
- Run the following commands from the repository root. Use the same device and comparable thermal conditions for version comparisons.

## 1. Connect the TV

For USB connections, authorize the debugging prompt on the TV. For network debugging, first pair/connect according to your TV's Android version, then check:

```sh
adb devices -l