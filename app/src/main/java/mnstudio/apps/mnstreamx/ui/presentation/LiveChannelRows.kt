package mnstudio.apps.mnstreamx.ui.presentation

import mnstudio.apps.mnstreamx.domain.model.Channel
import java.text.Collator
import java.util.Locale

internal enum class LiveChannelSort { PROVIDER, NAME, FAVORITES }
internal data class LiveChannelRow(val key: String, val title: String, val variants: List<Channel>, val colorIndex: Int = 0, val preferenceKey: String = key, val number: Int = 0) {
    fun channel(preferred: String?, defaults: Map<String, String> = emptyMap()): Channel =
        variants.firstOrNull { it.id == defaults[preferenceKey] } ?: variants.firstOrNull { it.id == preferred } ?: variants.first()
    val qualities: List<String> get() = variants.mapNotNull(HomeChannelPresentation::quality).distinct()
    val favorite: Boolean get() = variants.any { it.isFavorite }
    override fun toString() = "LiveChannelRow(variants=${variants.size})"
}

/** Only explicit quality suffixes, identical provider group and identical language may be folded. */
internal object LiveChannelRows {
    private val rank = listOf("8K", "4K", "UHD", "FHD", "HD", "SD")
    fun title(channel: Channel): String = LiveChannelMetadata.title(channel)
    fun category(value: String) = LiveChannelMetadata.category(value)
    fun project(channels: List<Channel>, sort: LiveChannelSort, locale: Locale,
        metadata: LiveChannelMetadataCache = LiveChannelMetadataCache(), checkpoint: () -> Unit = {}): List<LiveChannelRow> {
        val groups = linkedMapOf<List<String>, MutableList<Channel>>()
        channels.forEach { channel ->
            checkpoint()
            groups.getOrPut(metadata.get(channel).family) { mutableListOf() }.add(channel)
        }
        val rows = groups.entries.mapIndexed { index, (family, group) ->
            checkpoint()
            // Most provider families have one stream. Avoid allocating a deduplication
            // set and sort buffers for every single-channel row in a large catalog.
            val variants = if (group.size == 1) listOf(group[0]) else group.distinctBy { it.id }.sortedBy {
                rank.indexOf(metadata.get(it).quality).takeIf { n -> n >= 0 } ?: Int.MAX_VALUE
            }
            LiveChannelRow(group.first().id,metadata.get(group.first()).title,variants,index,metadata.familyKey(family))
        }
        return when (sort) {
            LiveChannelSort.PROVIDER -> rows
            LiveChannelSort.NAME -> {
                val collator = Collator.getInstance(locale)
                rows.sortedWith { a,b -> checkpoint();collator.compare(a.title,b.title) }
            }
            LiveChannelSort.FAVORITES -> rows.sortedWith { a,b -> checkpoint();b.favorite.compareTo(a.favorite) }
        }.also { checkpoint() }
    }
}