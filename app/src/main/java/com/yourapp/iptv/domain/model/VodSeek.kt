package com.yourapp.iptv.domain.model

object VodSeek {
    const val STEP_MS = 10_000L
    fun target(position: Long, duration: Long, delta: Long): Long? {
        if (duration <= 0) return null
        val start = position.coerceIn(0L, duration)
        return if (delta >= 0) start + delta.coerceAtMost(duration - start)
        else start - (if (delta == Long.MIN_VALUE) Long.MAX_VALUE else -delta).coerceAtMost(start)
    }
    fun time(value: Long): String {
        if (value < 0) return "–:––"
        val seconds = value / 1000
        return if (seconds >= 3600) "%d:%02d:%02d".format(java.util.Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%02d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
    }
}