package com.yourapp.iptv.data.datastore

import com.yourapp.iptv.domain.model.WatchHistory
import com.yourapp.iptv.domain.model.WatchHistoryEntry

/** A run of record events, never extending across an explicit remove/clear barrier. */
internal class HistoryRecordBatch {
    private val sources = linkedMapOf<String, Records>()
    val sourceCount get() = sources.size
    val entryCount get() = sources.values.sumOf { it.size }

    fun offer(source: String, entry: WatchHistoryEntry): Boolean {
        val previous = sources[source]
        if (previous == null && sources.size >= MAX_SOURCES) return false
        // Latest source use defines retention order, including A, B, A interleavings.
        val records = previous ?: Records()
        sources.remove(source);records.add(entry);sources[source] = records
        return true
    }
    fun entries(): List<Pair<String, Records>> = sources.entries.map { it.key to it.value }

    /** Exact WatchHistory.record fold: retain only the last 20 unique IDs, plus timestamp offsets. */
    internal class Records {
        private data class Pending(val id: String, val floor: Long, val offset: Long)
        private val rows = linkedMapOf<String, Pending>()
        private var count = 0L
        private var floor = -1L
        val size get() = rows.size
        fun add(entry: WatchHistoryEntry) {
            count = advance(count,1)
            floor = maxOf(entry.watchedAtMillis,advance(floor,1))
            rows.remove(entry.channelId);rows[entry.channelId] = Pending(entry.channelId,floor,count)
            if (rows.size > WatchHistory.LIMIT) rows.remove(rows.keys.first())
        }
        fun apply(previous: List<WatchHistoryEntry>): List<WatchHistoryEntry> {
            val base = previous.maxOfOrNull { it.watchedAtMillis.coerceAtMost(Long.MAX_VALUE - 1) } ?: -1L
            val recent = rows.values.reversed().map {
                WatchHistoryEntry(it.id,maxOf(it.floor,advance(base,it.offset)))
            }
            return (recent + previous.filterNot { it.channelId in rows }).take(WatchHistory.LIMIT)
        }
        private fun advance(value: Long, amount: Long) =
            if (value >= Long.MAX_VALUE - amount) Long.MAX_VALUE else value + amount
    }
    companion object { const val MAX_SOURCES = 32 }
}