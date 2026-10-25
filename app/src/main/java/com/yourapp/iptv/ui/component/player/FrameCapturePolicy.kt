package com.yourapp.iptv.ui.component.player

import com.yourapp.iptv.domain.model.Channel

/** A first-frame event belongs to one playback request, including same-channel resumes. */
internal data class RenderedChannelFrame(val channel: Channel, val mediaId: String) {
    fun matches(selected: Channel, playingMediaId: String?): Boolean =
        selected.id == channel.id && selected.streamUrl == channel.streamUrl && playingMediaId == mediaId
}

/** Bounded retries for a decoder that is already playing; no extra streams. */
internal enum class FrameCaptureResult {
    SAVED, ALREADY_CACHED, SURFACE_NOT_READY, EMPTY_IMAGE,
    PROTECTED_OR_INVALID, COPY_FAILED, SAVE_FAILED, STALE_SESSION;

    val mayRetry: Boolean
        get() = this == SURFACE_NOT_READY || this == EMPTY_IMAGE || this == COPY_FAILED
}

internal object FrameCapturePolicy {
    const val MAX_ATTEMPTS = 6

    fun delayBeforeMs(attempt: Int): Long {
        require(attempt in 0 until MAX_ATTEMPTS)
        return longArrayOf(150, 350, 700, 1200, 1600, 2000)[attempt]
    }

    fun isProtected(windowFlags: Int?, secureFlag: Int): Boolean =
        windowFlags != null && windowFlags and secureFlag != 0

    fun continueAfter(result: FrameCaptureResult, attemptsCompleted: Int): Boolean =
        attemptsCompleted in 1 until MAX_ATTEMPTS && result.mayRetry
}