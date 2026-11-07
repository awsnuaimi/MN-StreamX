package mnstudio.apps.mnstreamx.domain.caption

import mnstudio.apps.mnstreamx.domain.model.CaptionSettings
import mnstudio.apps.mnstreamx.domain.repository.CaptionDeviceStats
import org.junit.Assert.*
import org.junit.Test

class CaptionTextQualityTest {
    @Test fun decoderLoopsAndControlTokensAreRejectedWithoutRemovingNormalMultilingualDialogue() {
        for (text in listOf("صباح الخير", "Guten Morgen!", "Dzień dobry", "नमस्ते दुनिया", "No, no, no!", "2026"))
            assertTrue(text, CaptionTextQuality.usable(text))
        for (text in listOf("", "   ", "<|nospeech|>", "thank you ".repeat(5), "نعم ".repeat(8), "hello world again ".repeat(4)))
            assertFalse(text, CaptionTextQuality.usable(text))
        assertTrue(CaptionTextQuality.usable("You said thank you and then I said thank you."))
    }
    @Test fun dialectTagsAreCanonicalizedBeforeNativeLanguageSelection() {
        assertEquals("de", CaptionSettings(source = "de-DE").normalized().source)
        assertEquals("hi", CaptionSettings(source = "hi-IN").normalized().source)
        assertEquals("auto", CaptionSettings(source = "unknown").normalized().source)
    }
    @Test fun unknownLowMemoryAnd32BitDevicesNeverRetainBothStages() {
        assertFalse(CaptionWorkingSetPolicy.retain(null))
        for (stats in listOf(CaptionDeviceStats("armeabi-v7a", 4, 4096, 2048),
            CaptionDeviceStats("arm64-v8a", 4, 2048, 1536), CaptionDeviceStats("arm64-v8a", 4, 4096, 700)))
            assertFalse(CaptionWorkingSetPolicy.retain(stats))
        assertTrue(CaptionWorkingSetPolicy.retain(CaptionDeviceStats("arm64-v8a", 4, 4096, 1536)))
        assertEquals(3500, CaptionWorkingSetPolicy.initialWindow(CaptionDeviceStats("armeabi-v7a", 4, 1336, 500)))
        assertEquals(5000, CaptionWorkingSetPolicy.initialWindow(null))
    }
}