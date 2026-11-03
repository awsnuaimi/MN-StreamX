# MN StreamX — Android TV Benchmark Guide (v35.0.46.0)

## Scope

The eight `VodPerformanceBenchmark.kt` instrumentation tests measure isolated operations using synthetic data. They cover VOD filtering, incremental search, focus bookmark resolution (unchanged/reordered/removed selection), category switching, and compressed VOD cache reads/writes.

**Not measured:** actual DPAD-to-focus latency, Compose frame times, application startup, playback quality, or total memory usage. Successful GitHub Actions compilation is **not** evidence that benchmarks ran on an Android TV device.

## Prerequisites

- A physical Android TV device with Developer options and USB or wireless debugging enabled.
- A computer with Android SDK (`adb`), JDK 17, Gradle 8.7, and the Android SDK components required by the repository.
- The checked-out MN StreamX repository and a working device connection.
- A stable test environment: keep the TV awake, close unrelated apps, and avoid comparing measurements taken under very different thermal conditions.

The repository's CI uses a system `gradle` command; the archive may not contain a Gradle wrapper (`gradlew`).

## 1. Verify the device

```sh
adb devices