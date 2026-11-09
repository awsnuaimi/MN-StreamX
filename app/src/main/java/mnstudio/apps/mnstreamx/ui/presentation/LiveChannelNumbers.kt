package mnstudio.apps.mnstreamx.ui.presentation

import mnstudio.apps.mnstreamx.domain.model.Channel

/** Stable one-based numbers in provider order, independent of sorting or visibility. */
internal class LiveChannelNumbers {
    private var source: List<Channel>? = null
    private var cached: Map<String, Int> = emptyMap()

    @Synchronized
    fun forCatalog(channels: List<Channel>, metadata: LiveChannelMetadataCache = LiveChannelMetadataCache(),
        checkpoint: () -> Unit = {}): Map<String, Int> {
        checkpoint()
        if (source === channels) return cached

        // Number *families*, not rows. Constructing presentation rows here used to
        // allocate a second full catalog, sort every quality group and then discard it.
        val seen = HashSet<List<String>>()
        val numbers = LinkedHashMap<String, Int>()
        for (channel in channels) {
            checkpoint()
            val family = metadata.get(channel).family
            if (seen.add(family)) numbers[metadata.familyKey(family)] = numbers.size + 1
        }
        checkpoint() // A cancelled build must never replace a valid catalog index.
        source = channels
        cached = numbers
        return numbers
    }

    fun apply(rows: List<LiveChannelRow>, numbers: Map<String, Int>): List<LiveChannelRow> =
        rows.map { it.copy(number = numbers[it.preferenceKey] ?: 0) }
}