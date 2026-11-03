# MN StreamX — v35.0.45.3 Android TV Performance Benchmarking

## Purpose

This stage adds an opt-in AndroidX Microbenchmark target. It measures representative
operations on the connected Android device instead of treating JVM stress-test
execution time as Android TV performance.

The benchmark inputs are synthetic and never contact an IPTV provider. No account
credentials, provider URLs, or real catalog data are included.

## Benchmarks

`app/src/androidTest/.../VodPerformanceBenchmark.kt` contains:

1. Full category + search filtering over 60,000 synthetic VOD titles.
2. Incremental search using the previous matching result as the candidate set.
3. Focus restoration at a stable index in a 60,000-key list.
4. Reading a compressed 2,000-title VOD snapshot from app-private cache storage.
5. Writing a compressed 2,000-title VOD snapshot to app-private cache storage.

The AndroidX benchmark report measures these operations on the device. The app's
existing `PerformanceJournal` remains responsible for runtime p50/p95 summaries
for real user sessions, including provider fetch, cache read/write, catalog-ready,
category-ready, JSON parsing, and first grid layout.

## Run on an Android TV device

1. Connect a physical Android TV device with USB or wireless ADB enabled.
2. Keep the device awake, close unrelated heavy applications, and avoid running
   while the device is thermally throttled or in battery-saver mode.
3. From the project root, run:

   ```bash
   gradle :app:connectedBenchmarkAndroidTest