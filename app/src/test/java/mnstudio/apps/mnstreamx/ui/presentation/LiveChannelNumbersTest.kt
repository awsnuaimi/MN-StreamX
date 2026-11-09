package mnstudio.apps.mnstreamx.ui.presentation

import mnstudio.apps.mnstreamx.domain.model.Channel
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class LiveChannelNumbersTest {
    private val channels = listOf(Channel("opaque-900", "PL| Zeta HD", streamUrl = "one", group = "G"),
        Channel("opaque-2", "PL| Alpha HD", streamUrl = "two", group = "G"),
        Channel("opaque-3", "PL| Zeta FHD", streamUrl = "three", group = "G"),
        Channel("opaque-4", "AR| Zeta HD", streamUrl = "four", group = "G"))
    private fun rows(values: List<Channel>, sort: LiveChannelSort = LiveChannelSort.PROVIDER) =
        LiveChannelRows.project(values, sort, Locale.GERMAN)
    @Test fun providerFamiliesGetOneBasedNumbersWithoutInterpretingOpaqueIds() {
        val numbers = LiveChannelNumbers(); val result = numbers.apply(rows(channels), numbers.forCatalog(channels))
        assertEquals(listOf(1, 2, 3), result.map { it.number }); assertEquals(2, result.first().variants.size)
        assertEquals("opaque-900", result.first().key)
    }
    @Test fun filteringHidingAndSortingPreserveNumbersAndLeaveGaps() {
        val numbers = LiveChannelNumbers(); val index = numbers.forCatalog(channels)
        val filtered = numbers.apply(rows(channels.drop(1).filter { it.id != "opaque-3" }, LiveChannelSort.NAME), index)
        assertEquals(mapOf("opaque-2" to 2, "opaque-4" to 3), filtered.associate { it.key to it.number })
        assertEquals(listOf(2, 1, 3), numbers.apply(rows(channels, LiveChannelSort.NAME), index).map { it.number })
    }
    @Test fun qualityAvailabilityDoesNotChangeAFamilysNumber() {
        val numbers = LiveChannelNumbers(); val index = numbers.forCatalog(channels)
        assertEquals(1, numbers.apply(rows(listOf(channels[2])), index).single().number)
    }
    @Test fun oneCatalogIsCachedAndReplacementCatalogCannotReuseItsOldAssignments() {
        val numbers = LiveChannelNumbers(); val first = numbers.forCatalog(channels)
        assertSame(first, numbers.forCatalog(channels))
        val reversed = channels.reversed(); val changed = numbers.forCatalog(reversed)
        assertNotSame(first, changed); assertEquals(1, numbers.apply(rows(reversed), changed).first().number)
    }
    @Test fun unknownFamiliesCannotInheritAValidNumber() {
        val numbers = LiveChannelNumbers(); val index = numbers.forCatalog(channels)
        val other = Channel("other", "Other HD", streamUrl = "other")
        assertEquals(0, numbers.apply(rows(listOf(other)), index).single().number)
        assertTrue(LiveChannelNumbers().forCatalog(emptyList()).isEmpty())
    }
    @Test fun aLargeCatalogRetainsEveryNumberAndVariantWithoutCatalogMutation() {
        val source = (0 until 7490).map { Channel("opaque:$it", "Synthetic $it HD", streamUrl = "$it") }
        val numbers = LiveChannelNumbers(); val index = numbers.forCatalog(source)
        val result = numbers.apply(rows(source.takeLast(20)), index)
        assertEquals((7471..7490).toList(), result.map { it.number }); assertEquals(7490, source.size)
    }
    @Test fun newNumberingMatchesProviderFamiliesEvenWithDuplicateQualityStreams() {
        val duplicated = listOf(
            Channel("a", "PL| Music FHD", streamUrl = "url:a", group = "PL"),
            Channel("a", "PL| Music FHD", streamUrl = "url:a", group = "PL"),
            Channel("b", "PL| Music HD", streamUrl = "url:b", group = "PL"),
            Channel("c", "PL| News HD", streamUrl = "url:c", group = "PL"),
            Channel("d", "DE| Music HD", streamUrl = "url:d", group = "DE")
        )
        val actual = LiveChannelNumbers().forCatalog(duplicated)
        val expected = rows(duplicated).mapIndexed { index, row -> row.preferenceKey to index + 1 }.toMap()
        assertEquals(expected, actual)
        assertEquals(3, actual.size)
        assertEquals(1, actual[rows(duplicated).first().preferenceKey])
    }

    @Test fun cancelledCalculationNeverPublishesPartiallyNumberedCatalog() {
        val owner = LiveChannelNumbers()
        val first = owner.forCatalog(channels)
        val next = channels.reversed()
        var checkpoints = 0
        try {
            owner.forCatalog(next) {
                checkpoints++
                if (checkpoints == 3) throw IllegalStateException("cancelled")
            }
            fail("expected cancellation")
        } catch (expected: IllegalStateException) {
            assertEquals("cancelled", expected.message)
        }
        assertSame(first, owner.forCatalog(channels))
        assertEquals(rows(next).mapIndexed { i, row -> row.preferenceKey to i + 1 }.toMap(), owner.forCatalog(next))
    }
}