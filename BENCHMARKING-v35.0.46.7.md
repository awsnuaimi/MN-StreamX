# MN StreamX — Android TV Benchmarking (v35.0.46.7)

This guide describes running the eight AndroidX microbenchmarks on a real Android TV device.
These measurements are not UI frame-time, playback, or startup benchmarks.

## Prerequisites

- Python 3, JDK 17, Android SDK with ADB, and Gradle available on PATH.
- The repository has no Gradle wrapper (`gradlew`); install a compatible Gradle version.
- Enable developer options and debugging on the Android TV device.
- Authorize the debugging connection on the device.
- Run commands from the project root unless explicitly noted.

## 1. Connect the device

For a USB-connected TV, authorize the debugging prompt.
For network debugging, use your device's pairing or connection procedure.

```sh
adb devices -l