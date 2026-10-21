package com.yourapp.iptv.domain.caption

import com.yourapp.iptv.domain.model.CaptionCue
import org.junit.Assert.*
import org.junit.Test

class CaptionPresentationTest {
    @Test fun futureAndExpiredCuesAreHiddenWhileLateCuesHaveABoundedReadingTime() {
        val cue = CaptionCue("subtitle", 5000000, 9000000, 1000)
        assertEquals("", CaptionPresentation.visible(cue, 4000000, 1500))
        assertEquals("subtitle", CaptionPresentation.visible(cue, 6000000, 1500))
        assertEquals("subtitle", CaptionPresentation.visible(cue, 12000000, 3000))
        assertEquals("", CaptionPresentation.visible(cue, 12000000, 7600))
        assertEquals("", CaptionPresentation.visible(null, 0, 0))
    }
}