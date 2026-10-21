package com.yourapp.iptv.domain.model

data class WatchHistoryEntry(val channelId: String, val watchedAtMillis: Long)
data class WatchedChannel(val channel: Channel, val watchedAtMillis: Long)

object WatchHistory {
    const val LIMIT = 20
    fun record(rows: List<WatchHistoryEntry>, entry: WatchHistoryEntry): List<WatchHistoryEntry> {
        require(entry.channelId.isNotBlank() && entry.channelId.length <= 512 && entry.watchedAtMillis >= 0)
        val last = rows.maxOfOrNull { it.watchedAtMillis.coerceAtMost(Long.MAX_VALUE - 1) } ?: -1L
        val stamped = entry.copy(watchedAtMillis = maxOf(entry.watchedAtMillis, last + 1))
        return (listOf(stamped) + rows.filterNot { it.channelId == entry.channelId }).take(LIMIT)
    }
    fun project(rows: List<WatchHistoryEntry>, channels: Map<String, Channel>): List<WatchedChannel> =
        rows.distinctBy { it.channelId }.take(LIMIT).mapNotNull { row ->
            channels[row.channelId]?.let { WatchedChannel(it, row.watchedAtMillis) }
        }
}