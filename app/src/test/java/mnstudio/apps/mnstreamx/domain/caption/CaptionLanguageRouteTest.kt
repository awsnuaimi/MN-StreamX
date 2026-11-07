package mnstudio.apps.mnstreamx.domain.caption
import mnstudio.apps.mnstreamx.domain.model.CaptionLanguage
import org.junit.Assert.*
import org.junit.Test
class CaptionLanguageRouteTest {
    @Test fun everyRouteStartsAndEndsAtTheRequestedLanguages() {
        for (from in CaptionLanguage.entries) for (to in CaptionLanguage.entries) {
            val route = CaptionLanguageRoute.steps(from.tag, to.tag)
            if (from == to) { assertTrue(route.isEmpty()); continue }
            assertEquals(from.tag, route.first().first); assertEquals(to.tag, route.last().second)
            assertEquals(if (from.tag == "en" || to.tag == "en") 1 else 2, route.size)
            if (route.size == 2) assertEquals(route[0].second, route[1].first)
        }
    }
    @Test fun trackCodesNormalizeWithoutConfusingHindiAndUrdu() {
        assertEquals(CaptionLanguage.GERMAN, CaptionLanguage.fromTag("DEU-de"))
        assertEquals(CaptionLanguage.HINDI, CaptionLanguage.fromTag("hin_IN"))
        assertEquals(CaptionLanguage.POLISH, CaptionLanguage.fromTag("POL"))
        assertNull(CaptionLanguage.fromTag("ur")); assertNull(CaptionLanguage.fromTag("und"))
    }
    @Test(expected = IllegalArgumentException::class)
    fun unknownSourceCannotFallBackToEnglish() { CaptionLanguageRoute.steps("ur", "ar") }
}