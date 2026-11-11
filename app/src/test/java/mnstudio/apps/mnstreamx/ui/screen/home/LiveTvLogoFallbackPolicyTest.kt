package mnstudio.apps.mnstreamx.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTvLogoFallbackPolicyTest {
    @Test fun failedPreferredQualitySelectsTheNextLogoExactlyOnce() {
        assertEquals(1, LiveTvLogoFallbackPolicy.nextIndex(0, 0, 2, false))
        assertEquals(1, LiveTvLogoFallbackPolicy.nextIndex(1, 0, 2, false))
    }

    @Test fun lateErrorFromPreviousQualityCannotSkipValidFallback() {
        assertEquals(1, LiveTvLogoFallbackPolicy.nextIndex(1, 0, 2, false))
        assertFalse(LiveTvLogoFallbackPolicy.acceptsSuccess(1, 0))
        assertTrue(LiveTvLogoFallbackPolicy.acceptsSuccess(1, 1))
    }

    @Test fun errorAfterSuccessCannotRemoveVisibleLogo() {
        assertEquals(1, LiveTvLogoFallbackPolicy.nextIndex(1, 1, 2, true))
    }

    @Test fun candidateIndexStopsAfterAllQualitiesFail() {
        assertEquals(2, LiveTvLogoFallbackPolicy.nextIndex(1, 1, 2, false))
        assertEquals(2, LiveTvLogoFallbackPolicy.nextIndex(2, 2, 2, false))
    }

    @Test fun emptyCandidatesRemainAtFallbackIcon() {
        assertEquals(0, LiveTvLogoFallbackPolicy.nextIndex(0, 0, 0, false))
    }
}