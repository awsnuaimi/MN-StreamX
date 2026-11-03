# MN StreamX — Android TV Benchmark Guide (v35.0.45.5)

The existing `VodPerformanceBenchmark.kt` suite measures eight isolated operations on synthetic VOD data. It does **not** measure Compose frame times, DPAD-to-focus latency, app startup, or memory consumption. GitHub Actions only compiles benchmark tests; a successful APK build is not a device benchmark run.

## Run on an Android TV device

1. Enable developer options and USB/wireless debugging on the device.
2. Install Android SDK platform-tools on the host computer, connect the TV, and run `adb devices`. Confirm the device is listed as `device`.
3. Keep the TV awake, avoid concurrent workloads, and allow it to cool between runs.
4. From the project root, with Android SDK and Gradle installed, execute:

   ```sh
   gradle --no-daemon :app:connectedBenchmarkAndroidTest