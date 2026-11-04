package com.yourapp.iptv.domain.image

enum class ImageLoadResult { MEMORY, DISK, NETWORK, LOCAL, FAILED, CANCELLED }

/** Numeric session totals only. No image keys, URLs, drawables, or exceptions. */
data class ImageLoadSummary(
    val started: Long, val inFlight: Long,
    val memory: Long, val disk: Long, val network: Long, val local: Long,
    val failed: Long, val cancelled: Long,
    val recent: Int, val lastMs: Long?, val p50Ms: Long?, val p95Ms: Long?, val maxMs: Long?, val capped: Int
)

/** Cache accounting is not a measurement of total image, player, or GPU memory. */
data class ImageCacheUsage(val usedBytes: Long, val limitBytes: Long)

data class ImageDiagnosticsSnapshot(
    val memory: ImageCacheUsage?, val disk: ImageCacheUsage?, val loads: ImageLoadSummary
)