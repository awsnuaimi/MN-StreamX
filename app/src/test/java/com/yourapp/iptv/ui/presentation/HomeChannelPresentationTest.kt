package com.yourapp.iptv.ui.presentation

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.state.CaptionUiState
import org.junit.Assert.*
import org.junit.Test

class HomeChannelPresentationTest {
    private fun channel(id: String, name: String) = Channel(id, name, streamUrl = "https://synthetic.invalid/$id")
    private val ar = channel("ar", "AR | قناة 1 FHD")
    private val pl = channel("pl", "PL| TVN STYLE HD")
    private val unknown = channel("unknown", "BEE| Something")
    private val generated = CaptionUiState(CaptionSettings(mode = CaptionMode.GENERATED, target = CaptionLanguage.ARABIC), loaded = true)

    @Test fun onlyExplicitSupportedPrefixesAndQualitySuffixesAreUsed() {
        assertEquals("ar", HomeChannelPresentation.language(ar)); assertEquals("pl", HomeChannelPresentation.language(pl))
        assertNull(HomeChannelPresentation.language(unknown))
        assertNull(HomeChannelPresentation.language(channel("x", "ARABIC NETWORK")))
        assertEquals("FHD", HomeChannelPresentation.quality(ar)); assertEquals("HD", HomeChannelPresentation.quality(pl))
        assertNull(HomeChannelPresentation.quality(channel("x", "HD Channel News")))
    }
    @Test fun translationBadgesDescribeAConfiguredPreferenceAndNeverInventAnUnknownSource() {
        assertEquals("AR", HomeChannelPresentation.badge(ar, generated))
        assertEquals("PL → AR", HomeChannelPresentation.badge(pl, generated))
        assertNull(HomeChannelPresentation.badge(unknown, generated))
        val off = generated.copy(settings = generated.settings.copy(mode = CaptionMode.OFF))
        assertEquals("PL", HomeChannelPresentation.badge(pl, off))
        val cloud = generated.copy(settings = generated.settings.copy(translationBackend = TranslationBackend.CLOUD))
        assertEquals(HomeTranslationKind.SETUP, HomeChannelPresentation.kind(cloud))
        assertEquals("PL", HomeChannelPresentation.badge(pl, cloud))
        assertEquals("PL → AR", HomeChannelPresentation.badge(pl, cloud.copy(cloudConfigured = true)))
        assertEquals(HomeTranslationKind.UNKNOWN, HomeChannelPresentation.kind(generated.copy(readFailed = true)))
    }
    @Test fun languageRowsPreferOneNativeChannelThenRelevantFavoritesAndRecentChannels() {
        val selection = HomeDashboardSelection(listOf(pl), emptyList(), pl)
        val row = HomeChannelPresentation.select(listOf(pl, unknown, ar), selection, generated, HomeLanguage.GERMAN)
        assertTrue(row.languageSpecific); assertEquals(listOf(ar, pl), row.channels)
        val off = generated.copy(settings = generated.settings.copy(mode = CaptionMode.OFF))
        assertEquals(listOf(ar), HomeChannelPresentation.select(listOf(pl, ar), selection, off, HomeLanguage.ARABIC).channels)
        assertFalse(HomeChannelPresentation.select(listOf(unknown), HomeDashboardSelection(emptyList(), emptyList(), null),
            off, HomeLanguage.GERMAN).languageSpecific)
    }
    @Test fun projectionsStayBoundedAndDoNotMergeDifferentQualityOrFavoriteIdentities() {
        val rows = (0 until 7490).map { channel("$it", "AR| Sender $it HD") }
        val result = HomeChannelPresentation.select(rows, HomeDashboardSelection(emptyList(), rows.take(5), null),
            generated, HomeLanguage.ARABIC)
        assertEquals(12, result.channels.size); assertEquals(12, result.channels.map { it.id }.distinct().size)
        assertEquals(rows.take(12), result.channels)
        assertFalse(result.toString().contains("https://"))
    }
}