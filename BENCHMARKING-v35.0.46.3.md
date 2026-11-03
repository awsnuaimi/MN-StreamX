# MN StreamX — Android TV Benchmark Guide (v35.0.46.3)

## Purpose and limits

Eight AndroidX microbenchmarks in `app/src/androidTest/java/com/yourapp/iptv/performance/VodPerformanceBenchmark.kt` exercise synthetic VOD search/filter, incremental search, focus restoration, category switching and compressed cache I/O. They do **not** measure UI frame pacing, remote-control latency, cold startup, playback quality or memory consumption. GitHub Actions compiles the instrumentation tests but does not run them on a TV.

## Prerequisites

- A checkout of this repository on a computer with Python 3, JDK 17, Android SDK and ADB.
- Gradle 8.7 available as `gradle` (this repository does not ship a Gradle wrapper).
- An Android TV device with developer options and USB or network debugging enabled.
- An authorized ADB connection. Keep the device awake and thermal/background conditions consistent.

## 1. Connect and identify the TV

```sh
adb devices -l