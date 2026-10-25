package com.yourapp.iptv.ui.component.player

/** Bounded retries for a decoder that is already playing; no extra streams. */
internal enum class FrameCaptureResult {
    SAVED, ALREADY_CACHED, SURFACE_NOT_READY, EMPTY_IMAGE,
    PROTECTED_OR_INVALID, COPY_FAILED, SAVE_FAILED;

    val mayRetry: Boolean
        get() = this == SURFACE_NOT_READY || this == EMPTY_IMAGE || this == COPY_FAILED
}

internal object FrameCapturePolicy {
    const val MAX_ATTEMPTS = 6

    fun delayBeforeMs(attempt: Int): Long {
        require(attempt in 0 until MAX_ATTEMPTS)
        return if (attempt == 0) 900L else 1600L
    }

    fun continueAfter(result: FrameCaptureResult, attemptsCompleted: Int): Boolean =
        attemptsCompleted in 1 until MAX_ATTEMPTS && result.mayRetry
}