# MN StreamX — Android TV Benchmark Guide (v35.0.45.6)

The eight `VodPerformanceBenchmark.kt` tests measure **isolated operations** with synthetic data. They do not measure DPAD-to-focus latency, Compose frames, full startup, or memory. GitHub Actions compiles the tests but does not run them on a TV.

## Run on an Android TV device

1. Enable developer options and USB/wireless debugging on the TV. Connect the TV to a computer with Android SDK and Gradle installed.
2. Run `adb devices` and confirm exactly the intended device is listed as `device` (not `unauthorized`). If multiple devices are connected, select the target with `ANDROID_SERIAL`.
3. Keep the TV awake, close unrelated applications, and run both baseline and candidate builds under similar temperature and power conditions.
4. From the repository root, run:

   ```sh
   gradle --no-daemon :app:connectedBenchmarkAndroidTest