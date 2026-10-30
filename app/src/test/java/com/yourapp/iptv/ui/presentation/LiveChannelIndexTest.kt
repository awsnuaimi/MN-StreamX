package com.yourapp.iptv.ui.presentation

import com.yourapp.iptv.domain.model.Channel
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.util.Random

class LiveChannelIndexTest {
    private fun rows(count: Int) = (0 until count).map { n ->
        val channel = Channel("id:$n", "Station $n HD", streamUrl = "synthetic:$n")
        LiveChannelRow(channel.id, channel.name, listOf(channel), number = n + 1)
    }
    @Test fun emptyAndUnknownInputsNeverResolveToTheFirstChannel() {
        val index = LiveChannelIndex.Empty
        assertEquals(-1, index.keyPosition(null)); assertEquals(-1, index.channelPosition("missing"))
        assertNull(index.forKey("missing")); assertNull(index.forChannel(null)); assertTrue(index.numberedRows.isEmpty())
    }
    @Test fun everyQualityVariantResolvesToItsActualFamilyAfterSorting() {
        val channels = listOf(Channel("hd", "PL| Zeta HD", streamUrl = "hd"),
            Channel("fhd", "PL| Zeta FHD", streamUrl = "fhd"), Channel("other", "Alpha HD", streamUrl = "other"))
        val index = LiveChannelIndex(LiveChannelRows.project(channels, LiveChannelSort.NAME, Locale.GERMAN))
        assertEquals(1, index.channelPosition("hd")); assertEquals(1, index.channelPosition("fhd"))
        assertSame(index.rows[1], index.forChannel("fhd")); assertEquals(0, index.channelPosition("other"))
    }
    @Test fun duplicateIdsKeepTheSameFirstMatchAsThePreviousLinearLookup() {
        val base = rows(2)
        val index = LiveChannelIndex(base + base[1].copy(key = "duplicate", variants = base[0].variants))
        assertEquals(0, index.channelPosition("id:0")); assertEquals(2, index.keyPosition("duplicate"))
        assertEquals(-1, index.channelPosition(""))
    }
    @Test fun filteringReorderingAndNewOwnersNeverReuseOldRowsOrNumbers() {
        val base = rows(20); val first = LiveChannelIndex(base)
        val second = LiveChannelIndex(base.takeLast(3).reversed())
        assertNull(second.forChannel("id:0")); assertEquals(0, second.channelPosition("id:19"))
        assertEquals(setOf(18, 19, 20), second.numberedRows.keys)
        val changed = base.last().variants.single().copy(streamUrl = "replacement")
        val owner = LiveChannelIndex(listOf(base.last().copy(variants = listOf(changed))))
        assertEquals("replacement", owner.forChannel("id:19")!!.variants.single().streamUrl)
        assertEquals("synthetic:19", first.forChannel("id:19")!!.variants.single().streamUrl)
    }
    @Test fun zeroAndNegativeNumbersAreNotRemoteTargetsAndGapsArePreserved() {
        val base = rows(4).mapIndexed { n, row -> row.copy(number = listOf(0, -1, 8, 25)[n]) }
        assertEquals(setOf(8, 25), LiveChannelIndex(base).numberedRows.keys)
    }
    @Test fun randomizedProjectionMatchesLinearLookupsForEveryVariantAndMissingKey() {
        val random = Random(37)
        repeat(25) {
            val values = rows(200).shuffled(kotlin.random.Random(random.nextInt())).take(120)
            val index = LiveChannelIndex(values)
            for (id in (0..210).map { "id:$it" } + listOf(null)) {
                assertEquals(values.indexOfFirst { row -> row.variants.any { it.id == id } }, index.channelPosition(id))
                assertEquals(values.indexOfFirst { row -> row.key == id }, index.keyPosition(id))
            }
        }
    }
    @Test fun largeCatalogLookupsAgreeWithTheBaselineWithoutTimingAssertions() {
        val values = rows(7490); val index = LiveChannelIndex(values)
        val ids = List(1000) { "id:${(it * 7919L % values.size).toInt()}" }
        fun linear() = ids.sumOf { id -> values.indexOfFirst { row -> row.variants.any { it.id == id } }.toLong() }
        fun indexed() = ids.sumOf { index.channelPosition(it).toLong() }
        assertEquals(linear(), indexed())
        // Optional local comparison only. Host JVM timings are not Android TV performance claims.
        if (System.getProperty("mnx.measure.index") == "true") {
            repeat(8) { linear(); indexed() }
            val old = mutableListOf<Long>(); val current = mutableListOf<Long>()
            repeat(32) { n ->
                fun sample(block: () -> Long): Long {
                    val start = System.nanoTime(); assertEquals(3_726_620L, block())
                    return System.nanoTime() - start
                }
                if (n % 2 == 0) { old += sample(::linear); current += sample(::indexed) }
                else { current += sample(::indexed); old += sample(::linear) }
            }
            fun stats(values: List<Long>) = values.sorted().let { "p50_ns=${it[15]} p95_ns=${it[30]}" }
            println("LIVE_INDEX_HOST catalog=7490 lookups=1000 samples=32 linear=${stats(old)} indexed=${stats(current)}")
        }
    }
}