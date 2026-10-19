package com.yourapp.iptv.ui.presentation

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ProgramPresentation {
    fun timeRange(start: Long, end: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(zone)
        return "${formatter.format(Instant.ofEpochMilli(start))} – ${formatter.format(Instant.ofEpochMilli(end))}"
    }

    fun channelCount(visible: Int, total: Int): String =
        "المعروض: ${isolate(visible.coerceAtLeast(0))} · الإجمالي: ${isolate(total.coerceAtLeast(0))}"

    fun playerPosition(position: Int, total: Int): String = if (position > 0 && position <= total)
        "القناة ${isolate(position)} من ${isolate(total)}" else "خارج القائمة · ${isolate(total.coerceAtLeast(0))} قناة"

    private fun isolate(value: Int) = "\u2066$value\u2069"
}