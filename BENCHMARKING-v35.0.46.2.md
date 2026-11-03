# MN StreamX — Android TV Benchmark Guide (v35.0.46.2)

## Scope

Eight AndroidX microbenchmarks in `app/src/androidTest/java/com/yourapp/iptv/performance/VodPerformanceBenchmark.kt` cover synthetic VOD search, filtering, focus restoration, category switching, and compressed cache I/O. They do **not** measure actual remote latency, UI frames, app startup, memory, or playback. GitHub Actions compiles these tests but does not execute them on a physical TV.

## Requirements

- A computer with Android SDK, ADB, JDK 17, Gradle compatible with this repository, and the required SDK packages.
- A physical Android TV device with developer options and USB/network debugging enabled.
- A checkout of this repository. Run commands from the repository root.
- Keep the device awake; use similar power, thermal and background-load conditions for comparable runs.
- Use a single authorized ADB device or specify its serial number.

## 1. Verify ADB connection

```sh
adb devices -l