package mnstudio.apps.mnstreamx.domain.caption

import mnstudio.apps.mnstreamx.domain.model.CaptionCue

object CaptionPresentation {
    // Speech inference and offline translation are sequential on low-RAM TVs.
    // Accept bounded delayed captions instead of silently discarding every
    // successful recognition on devices whose decode takes 9-10 seconds.
    const val MAX_WINDOW_LAG_MS = 20_000L
    private const val DEFAULT_WINDOW_LAG_MS = 8_000L
    /** Future cues wait for the audio clock; delayed cues get a bounded reading period. */
    fun visible(cue: CaptionCue?, positionUs: Long, nowMillis: Long): String {
        if (cue == null || nowMillis - cue.producedAtMillis !in 0..6500) return ""
        if (positionUs != Long.MIN_VALUE && positionUs < cue.startUs) return ""
        return cue.text
    }
    fun tooOld(window: AudioWindow, positionUs: Long,
        maxLagMillis: Long = DEFAULT_WINDOW_LAG_MS): Boolean = positionUs != Long.MIN_VALUE &&
        positionUs - (window.startUs + window.durationMillis * 1000) > maxLagMillis * 1000
}