# MN StreamX — Android TV Benchmark Guide (v35.0.46.1)

## What is measured

`app/src/androidTest/java/com/yourapp/iptv/performance/VodPerformanceBenchmark.kt` contains eight AndroidX microbenchmarks using synthetic VOD data: filtering, incremental search, focus restoration for stable/reordered/removed items, category switching, and compressed-cache read/write. These are operation-level measurements. They **do not** measure real remote-button latency, Compose frame timing, app startup, memory usage, or playback quality.

GitHub Actions compiles the benchmark tests but **does not run them on a TV**. Its Python test step validates the report, comparison, and device-runner scripts offline.

## Requirements

- Physical Android TV with developer options and ADB debugging enabled; connect it to a computer.
- Android SDK with `adb`, JDK 17, Gradle 8.7 and the Android SDK packages required by the project.
- Checked-out MN StreamX source; run all commands from the repository root.
- Prefer a single authorized ADB device. Keep the TV awake and thermal conditions similar between runs.

The GitHub workflow uses the system `gradle` executable. If your checkout has a working Gradle wrapper, you can substitute `./gradlew` (or `gradlew.bat` on Windows) in manual commands.

## 1. Check the connected device

```sh
adb devices -l