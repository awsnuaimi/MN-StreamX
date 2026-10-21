package com.yourapp.iptv.domain.caption

import com.yourapp.iptv.domain.model.CaptionCue

object CaptionPresentation {
    /** Future cues wait for the audio clock; delayed cues get a bounded reading period. */
    fun visible(cue: CaptionCue?, positionUs: Long, nowMillis: Long): String {
        if (cue == null || nowMillis - cue.producedAtMillis !in 0..6500) return ""
        if (positionUs != Long.MIN_VALUE && positionUs < cue.startUs) return ""
        return cue.text
    }
    fun tooOld(window: AudioWindow, positionUs: Long): Boolean = positionUs != Long.MIN_VALUE &&
        positionUs - (window.startUs + window.durationMillis * 1000) > 8000000
}