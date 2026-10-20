package com.yourapp.iptv.player.controller

/** Startup threshold is not a promise about server response time or keyframe availability. */
internal object LivePlaybackTuning {
    const val MIN_BUFFER_MS = 8_000
    const val MAX_BUFFER_MS = 20_000
    const val START_BUFFER_MS = 500
    const val REBUFFER_MS = 1_500
    const val TARGET_BUFFER_BYTES = 24 * 1024 * 1024
}