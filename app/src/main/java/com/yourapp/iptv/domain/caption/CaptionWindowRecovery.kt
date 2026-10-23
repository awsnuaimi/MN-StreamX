package com.yourapp.iptv.domain.caption

/** Adaptive live windows for native and translation deadlines; never replay stale audio. */
class CaptionWindowRecovery {
    var windowMillis = 5000
        private set
    private var consecutiveFailures = 0
    fun retry(): Boolean {
        consecutiveFailures++
        if (consecutiveFailures >= 3) return false
        windowMillis = if (windowMillis > 3500) 3500 else 2500
        return true
    }
    /** Throughput recovery cannot loop forever: only smaller windows are useful. */
    fun adaptThroughput(): Boolean {
        if (windowMillis <= 2500) return false
        windowMillis = if (windowMillis > 3500) 3500 else 2500
        return true
    }
    fun succeeded() { consecutiveFailures = 0 }
    fun accepts(window: AudioWindow) = window.durationMillis <= windowMillis
}