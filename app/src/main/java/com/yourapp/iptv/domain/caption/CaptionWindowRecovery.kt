package com.yourapp.iptv.domain.caption

/** Bounded recovery for isolated translation deadlines; no retry of stale audio. */
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
    fun succeeded() { consecutiveFailures = 0 }
    fun accepts(window: AudioWindow) = window.durationMillis <= windowMillis
}