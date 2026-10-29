package com.yourapp.iptv.ui.presentation

import com.yourapp.iptv.domain.model.Channel
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

internal enum class LiveChannelSort { PROVIDER, NAME, FAVORITES }
internal data class LiveChannelRow(val key: String, val title: String, val variants: List<Channel>, val colorIndex: Int = 0) {
    fun channel(preferred: String?): Channel = variants.firstOrNull { it.id == preferred } ?: variants.first()
    val qualities: List<String> get() = variants.mapNotNull(HomeChannelPresentation::quality).distinct()
    val favorite: Boolean get() = variants.any { it.isFavorite }
    override fun toString() = "LiveChannelRow(variants=${variants.size})"
}

/** Only explicit quality suffixes, identical provider group and identical language may be folded. */
internal object LiveChannelRows {
    private val prefix = Regex("^\\s*[A-Za-z]{2}\\s*[|:]\\s*")
    private val suffix = Regex("[\\s|:_-]+(?:8K|4K|UHD|FHD|HD|SD)$", RegexOption.IGNORE_CASE)
    private val rank = listOf("8K", "4K", "UHD", "FHD", "HD", "SD")
    fun title(channel: Channel): String = channel.name.trim().replaceFirst(prefix, "")
        .let { if (HomeChannelPresentation.quality(channel) != null) it.replace(suffix, "").trim() else it }
        .ifBlank { channel.name }
    fun category(value: String) = value.trim().replaceFirst(prefix, "").ifBlank { value }
    fun project(channels: List<Channel>, sort: LiveChannelSort, locale: Locale): List<LiveChannelRow> {
        val groups = linkedMapOf<List<String>, MutableList<Channel>>()
        channels.forEach { channel ->
            val quality = HomeChannelPresentation.quality(channel)
            val key = if (quality == null) listOf("id", channel.id, channel.streamUrl) else listOf("quality",
                channel.group.orEmpty(), channel.name.trim().let { prefix.find(it)?.value?.trim().orEmpty() },
                Normalizer.normalize(title(channel), Normalizer.Form.NFKC).lowercase(Locale.ROOT))
            groups.getOrPut(key) { mutableListOf() }.add(channel)
        }
        val rows = groups.values.mapIndexed { index, group ->
            val variants = group.distinctBy { it.id }.sortedBy {
                rank.indexOf(HomeChannelPresentation.quality(it)).takeIf { n -> n >= 0 } ?: Int.MAX_VALUE
            }
            LiveChannelRow(group.first().id, title(group.first()), variants, index)
        }
        val collator = Collator.getInstance(locale)
        return when (sort) {
            LiveChannelSort.PROVIDER -> rows
            LiveChannelSort.NAME -> rows.sortedWith { a, b -> collator.compare(a.title, b.title) }
            LiveChannelSort.FAVORITES -> rows.sortedByDescending { it.favorite }
        }
    }
}