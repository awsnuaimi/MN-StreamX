package com.yourapp.iptv.ui.presentation

import com.yourapp.iptv.domain.model.Channel

data class DeckChannelItem(val channel: Channel, val number: Int)

object PlayerDeckWindow {
    fun channels(channels: List<Channel>, currentIndex: Int, limit: Int = 10): List<DeckChannelItem> {
        if (channels.isEmpty()) return emptyList()
        val count = limit.coerceIn(1, 12).coerceAtMost(channels.size)
        val center = currentIndex.takeIf { it in channels.indices } ?: 0
        val start = (center - count / 2).coerceIn(0, channels.size - count)
        return (start until start + count).map { DeckChannelItem(channels[it], it + 1) }
    }

    fun remainingMinutes(end: Long, now: Long): Long? = if (end > now)
        kotlin.math.ceil((end.toDouble() - now.toDouble()) / 60000.0).toLong() else null

    fun remaining(end: Long, now: Long): String? = remainingMinutes(end, now)?.let { "متبقٍ $it د" }
}