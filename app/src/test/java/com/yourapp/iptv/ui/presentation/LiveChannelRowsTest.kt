package com.yourapp.iptv.ui.presentation

import com.yourapp.iptv.domain.model.Channel
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class LiveChannelRowsTest {
    private fun channel(id: String, name: String, group: String = "PL| GENERAL") =
        Channel(id, name, streamUrl = "https://synthetic.invalid/$id", group = group)
    private fun rows(vararg channels: Channel) = LiveChannelRows.project(channels.toList(), LiveChannelSort.PROVIDER, Locale.GERMAN)
    @Test fun explicitQualityVariantsFoldAndRetainEveryPlayableIdentity() {
        val low = channel("hd", "PL| TVN STYLE HD"); val high = channel("fhd", "PL| TVN STYLE FHD")
        val row = rows(low, high).single()
        assertEquals("TVN STYLE", row.title); assertEquals(listOf("FHD", "HD"), row.qualities)
        assertEquals("hd", row.key); assertEquals(high, row.channel(null)); assertEquals(low, row.channel("hd"))
        assertEquals("PL| TVN STYLE HD", low.name)
    }
    @Test fun languageAndProviderGroupNeverCross() {
        assertEquals(3, rows(channel("a", "PL| TVN HD"), channel("b", "AR| TVN FHD"),
            channel("c", "PL| TVN FHD", "PL| MOVIES")).size)
    }
    @Test fun unqualifiedNamesAreNeverGuessedToBeVariants() {
        val values = rows(channel("a", "TVN"), channel("b", "TVN"), channel("c", "TVN 1080p"), channel("d", "TVN FHD"))
        assertEquals(4, values.size); assertTrue(values[0].qualities.isEmpty())
        assertEquals("TVN 1080p", values[2].title)
    }
    @Test fun alphabeticAndFavoriteOrderingAreStableWithoutMutatingProviderOrder() {
        val source = listOf(channel("b", "Beta HD"), channel("a", "Alpha HD").copy(isFavorite = true))
        assertEquals(listOf("b", "a"), LiveChannelRows.project(source, LiveChannelSort.PROVIDER, Locale.ENGLISH).map { it.key })
        for (sort in listOf(LiveChannelSort.NAME, LiveChannelSort.FAVORITES))
            assertEquals(listOf("a", "b"), LiveChannelRows.project(source, sort, Locale.ENGLISH).map { it.key })
        assertEquals("b", source.first().id)
    }
    @Test fun qualityWordsInsideNamesAndEmptyCleanedNamesArePreserved() {
        assertEquals("HD NEWS", LiveChannelRows.title(channel("a", "PL| HD NEWS")))
        assertEquals("HD", LiveChannelRows.title(channel("b", "HD")))
        assertEquals("FHD", LiveChannelRows.title(channel("c", "PL| FHD")))
        assertEquals("POLAND VIP", LiveChannelRows.category("PL| POLAND VIP"))
    }
    @Test fun longAndMalformedProviderNamesDoNotLoseOrInventItems() {
        val source = (0 until 7490).map { channel("$it", "\u0000 أي قناة $it ${"س".repeat(60)} HD") }
        val rows = LiveChannelRows.project(source, LiveChannelSort.NAME, Locale.forLanguageTag("ar"))
        assertEquals(7490, rows.size); assertEquals(source.map { it.id }.toSet(), rows.flatMap { it.variants }.map { it.id }.toSet())
    }
}