package com.yourapp.iptv.ui.screen.home

import java.util.Locale

/** Small, stable identification mark for a channel with no usable artwork. */
internal object ChannelMonogram {
    private val whitespace = Regex("\\s+")
    private val generic = setOf(
        "HD", "FHD", "UHD", "4K", "8K", "SD", "CHANNEL", "CH", "TV",
        "قناة", "قناه", "قنوات"
    )

    fun of(name: String): String {
        val words = name.trim().split(whitespace).map { word ->
            word.filter { it.isLetterOrDigit() }
        }.filter { it.isNotEmpty() && it.uppercase(Locale.ROOT) !in generic }
        if (words.isEmpty()) return "?"

        val brand = words.first()
        val number = words.drop(1).firstOrNull {
            it.length in 1..2 && it.all(Char::isDigit)
        }
        val arabic = brand.any { it in '\u0600'..'\u06FF' }
        val unprefixed = if (arabic && brand.startsWith("ال") && brand.length > 3) {
            brand.drop(2)
        } else brand
        val normalized = unprefixed.uppercase(Locale.ROOT)
        val mark = if (arabic) {
            normalized.take(2)
        } else when {
            number == null -> normalized.take(4)
            normalized.length <= 4 -> normalized
            else -> normalized.take(2)
        }
        return (mark + (number ?: "")).take(5).ifEmpty { "?" }
    }
}