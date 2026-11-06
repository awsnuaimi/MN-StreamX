package mnstudio.apps.mnstreamx.ui.screen.vod

import mnstudio.apps.mnstreamx.domain.model.HomeLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VodSeekFeedbackLabelsTest {
    @Test fun ordinaryTenSecondButtonsKeepTheirExistingLabels() {
        for (language in HomeLanguage.values()) {
            val labels = VodPlaybackLabels(language)
            assertEquals(labels.forward(), labels.seekJump(10_000L))
            assertEquals(labels.rewind(), labels.seekJump(-10_000L))
        }
    }

    @Test fun fasterRemoteJumpsShowTheirActualDurationInEveryLanguage() {
        for (language in HomeLanguage.values()) {
            val labels = VodPlaybackLabels(language)
            assertTrue(labels.seekJump(30_000L).contains("30"))
            assertTrue(labels.seekJump(-60_000L).contains("60"))
            assertTrue(labels.timeline().isNotBlank())
        }
    }
}