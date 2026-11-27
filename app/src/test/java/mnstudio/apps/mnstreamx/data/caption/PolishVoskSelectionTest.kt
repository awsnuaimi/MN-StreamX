package mnstudio.apps.mnstreamx.data.caption

import mnstudio.apps.mnstreamx.domain.caption.CaptionResourcePolicy
import mnstudio.apps.mnstreamx.domain.model.CaptionLanguage
import mnstudio.apps.mnstreamx.domain.model.CaptionSettings
import mnstudio.apps.mnstreamx.domain.model.CaptionStage
import mnstudio.apps.mnstreamx.domain.model.TranslationBackend
import org.junit.Assert.*
import org.junit.Test

class PolishVoskSelectionTest {
    @Test fun onlyTheDedicatedBackendForcesPolish() {
        val choice = CaptionSettings(source = "auto", target = CaptionLanguage.ARABIC)
        val polish = choice.copy(translationBackend = TranslationBackend.OFFLINE_POLISH_VOSK).normalized()
        assertEquals("pl", polish.source)
        assertEquals(CaptionLanguage.ARABIC, polish.target)
        assertEquals("auto", choice.normalized().source)
    }

    @Test fun modelAndDownloadEstimateMatchEachDirection() {
        val speech = PolishVoskModels.bundle.assets.single()
        assertEquals(52979372L, speech.size)
        assertEquals(PolishVoskModels.FOLDER + ".zip", speech.path)
        assertEquals(speech.size, PolishVoskModels.downloadBytes("pl"))
        assertEquals(speech.size + BergamotCatalog.pack("pl", "en").bundle.bytes +
            BergamotCatalog.pack("en", "ar").bundle.bytes, PolishVoskModels.downloadBytes("ar"))
    }

    @Test fun polishColdAdmissionRespectsHeadroomAndAndroidPressure() {
        val stage = CaptionResourcePolicy.POLISH_VOSK_COLD
        val unit = CaptionResourcePolicy.MIB
        val low = CaptionResourcePolicy.memory(stage, 332 * unit, 0, false)
        assertFalse(low.allowed)
        assertEquals(CaptionStage.SPEECH, low.stage)
        assertTrue(CaptionResourcePolicy.memory(stage, 333 * unit, 0, false).allowed)
        assertFalse(CaptionResourcePolicy.memory(stage, 600 * unit, 0, true).allowed)
    }
}