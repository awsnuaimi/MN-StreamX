package mnstudio.apps.mnstreamx.data.caption
import mnstudio.apps.mnstreamx.domain.caption.CaptionLanguageRoute
import org.junit.Assert.*
import org.junit.Test
class CaptionModelCatalogTest {
    @Test fun sameLanguagesNeedOnlyTheirSourceRecognizer() {
        for (tag in listOf("ar", "en", "de", "pl", "hi")) assertEquals(CaptionSpeechPlan.speechBytes(tag), CaptionModelCatalog.totalBytes(tag, tag))
    }
    @Test fun eachDirectionIncludesItsExactTextPacks() {
        for (from in listOf("ar", "en", "de", "pl", "hi")) for (to in listOf("ar", "en", "de", "pl", "hi")) {
            val packs = CaptionLanguageRoute.steps(from, to).sumOf { (source, target) -> BergamotCatalog.pack(source, target).bundle.bytes }
            assertEquals(CaptionSpeechPlan.speechBytes(from) + packs, CaptionModelCatalog.totalBytes(to, from))
        }
    }
    @Test fun automaticBudgetCoversEveryDetectedSource() {
        val total = CaptionModelCatalog.totalBytes("ar", "auto")
        for (tag in listOf("ar", "en", "de", "pl", "hi")) assertTrue(total >= CaptionModelCatalog.totalBytes("ar", tag))
    }
}