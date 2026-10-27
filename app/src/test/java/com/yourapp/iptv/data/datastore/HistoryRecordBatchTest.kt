package com.yourapp.iptv.data.datastore

import com.yourapp.iptv.domain.model.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class HistoryRecordBatchTest {
    @Test fun compactedRunsMatchTheExistingHistoryContractForRandomInputsAndPriorRows() {
        val random = Random(203501)
        repeat(500) { sample ->
            val extremes = sample % 10 == 0
            var before = emptyList<WatchHistoryEntry>()
            repeat(random.nextInt(40)) { before = WatchHistory.record(before,entry(random,extremes)) }
            var expected = before
            val batch = HistoryRecordBatch()
            repeat(random.nextInt(1,500)) {
                val value = entry(random,extremes);expected = WatchHistory.record(expected,value)
                assertTrue(batch.offer("source",value));assertTrue(batch.entryCount <= WatchHistory.LIMIT)
            }
            assertEquals(expected,batch.entries().single().second.apply(before))
        }
    }
    @Test fun aHundredThousandEqualClockEventsKeepTheirExactLogicalTimestampsInTwentySlots() {
        val batch = HistoryRecordBatch()
        repeat(100_000) { assertTrue(batch.offer("source",WatchHistoryEntry("id-${it % 40}",0))) }
        assertEquals(1,batch.sourceCount);assertEquals(20,batch.entryCount)
        val rows = batch.entries().single().second.apply(listOf(WatchHistoryEntry("previous",500_000)))
        assertEquals((39 downTo 20).map { "id-$it" },rows.map { it.channelId })
        assertEquals((600_000L downTo 599_981L).toList(),rows.map { it.watchedAtMillis })
    }
    @Test fun repeatedChannelsDoNotResurrectOlderRowsOrLosePriorChannelsPrematurely() {
        val before = listOf(WatchHistoryEntry("old",100),WatchHistoryEntry("a",99))
        val batch = HistoryRecordBatch()
        listOf("a","b","a").forEach { batch.offer("source",WatchHistoryEntry(it,0)) }
        assertEquals(listOf(WatchHistoryEntry("a",103),WatchHistoryEntry("b",102),WatchHistoryEntry("old",100)),
            batch.entries().single().second.apply(before))
    }
    @Test fun overflowRejectsOnlyTheAdditionalSourceAndExistingSourcesRemainWritable() {
        val batch = HistoryRecordBatch()
        repeat(HistoryRecordBatch.MAX_SOURCES) { assertTrue(batch.offer("s$it",WatchHistoryEntry("a",1))) }
        assertFalse(batch.offer("extra",WatchHistoryEntry("not-accepted",2)))
        assertTrue(batch.offer("s0",WatchHistoryEntry("latest",3)))
        assertEquals(32,batch.sourceCount);assertEquals("s0",batch.entries().last().first)
        assertEquals(listOf("latest","a"),batch.entries().last().second.apply(emptyList()).map { it.channelId })
    }
    @Test fun sourceRetentionOrderFollowsTheLastEventForEachSource() {
        val batch = HistoryRecordBatch()
        listOf("A","B","A","C","B").forEach { batch.offer(it,WatchHistoryEntry("id",1)) }
        assertEquals(listOf("A","C","B"),batch.entries().map { it.first })
    }
    @Test fun timestampSaturationMatchesTheDomainContractWithoutOverflow() {
        val batch = HistoryRecordBatch();var expected = listOf(WatchHistoryEntry("old",Long.MAX_VALUE))
        repeat(50) { val e = WatchHistoryEntry("$it",Long.MAX_VALUE - it);batch.offer("s",e);expected = WatchHistory.record(expected,e) }
        val rows = batch.entries().single().second.apply(listOf(WatchHistoryEntry("old",Long.MAX_VALUE)))
        assertEquals(expected,rows);assertTrue(rows.all { it.watchedAtMillis == Long.MAX_VALUE })
    }
    private fun entry(random: Random, extremes: Boolean) = WatchHistoryEntry("id-${random.nextInt(50)}",when (if (extremes) random.nextInt(10) else 2) {
        0 -> Long.MAX_VALUE;1 -> Long.MAX_VALUE - 1;else -> random.nextLong(10_000)
    })
}