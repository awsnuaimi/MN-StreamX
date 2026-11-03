# MN StreamX — Android TV device benchmarks (v35.0.45.4)

These are **opt-in AndroidX Microbenchmarks**. GitHub Actions compiles them, but
compilation and ordinary unit tests do **not** execute device benchmarks.

## Coverage

The synthetic test suite in `VodPerformanceBenchmark.kt` measures:

1. Full category/search filtering across 60,000 titles.
2. Incremental filtering when a query is extended.
3. Restoring focus at an unchanged index in 60,000 stable keys.
4. Restoring focus after a catalog reorder (the saved index is stale).
5. Restoring focus when the selected title has been removed.
6. Switching categories without reusing stale incremental search results.
7. Reading a compressed snapshot of 2,000 titles from private cache.
8. Writing a compressed snapshot of 2,000 titles to private cache.

These are isolated operation timings, **not** end-to-end UI frame-time or
startup benchmarks. Focus tests measure key lookup, not DPAD input latency.
Cache tests measure filesystem and serialization behavior on the device.
All data is synthetic; no provider account or credentials are accessed.

## Run on a physical Android TV device

1. Enable developer options and USB or wireless debugging; connect the device.
2. Ensure the device is awake, thermally stable, and not running heavy apps.
3. Verify connectivity: `adb devices`.
4. From the repository root, with Android SDK and Gradle installed, run:

   ```bash
   gradle :app:connectedBenchmarkAndroidTest