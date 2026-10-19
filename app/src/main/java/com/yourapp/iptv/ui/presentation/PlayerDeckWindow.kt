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

    fun remaining(end: Long, now: Long): String? {
        if (end <= now) return null
        val difference = end.toDouble() - now.toDouble()
        val minutes = kotlin.math.ceil(difference / 60000.0).toLong()
        return "متبقٍ $minutes د"
    }
}