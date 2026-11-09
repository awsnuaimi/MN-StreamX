package mnstudio.apps.mnstreamx.domain.caption

import mnstudio.apps.mnstreamx.domain.model.CaptionCue
import org.junit.Assert.*
import org.junit.Test

class CaptionPresentationTest {
    @Test fun staleWindowGuardMatchesTheLiveLagBudget() {
        val w = AudioWindow(FloatArray(3500 * 16), 0)
        assertTrue(CaptionPresentation.tooOld(w, 17_000_000L))
        assertFalse(CaptionPresentation.tooOld(w, 17_000_000L, CaptionPresentation.MAX_WINDOW_LAG_MS))
        assertFalse(CaptionPresentation.tooOld(w, 23_500_000L, CaptionPresentation.MAX_WINDOW_LAG_MS))
        assertTrue(CaptionPresentation.tooOld(w, 23_500_001L, CaptionPresentation.MAX_WINDOW_LAG_MS))
    }
    @Test fun futureAndExpiredCuesAreHiddenWhileLateCuesHaveABoundedReadingTime() {
        val cue = CaptionCue("subtitle", 5000000, 9000000, 1000)
        assertEquals("", CaptionPresentation.visible(cue, 4000000, 1500))
        assertEquals("subtitle", CaptionPresentation.visible(cue, 6000000, 1500))
        assertEquals("subtitle", CaptionPresentation.visible(cue, 12000000, 3000))
        assertEquals("", CaptionPresentation.visible(cue, 12000000, 7600))
        assertEquals("", CaptionPresentation.visible(null, 0, 0))
    }
}