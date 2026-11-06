package mnstudio.apps.mnstreamx.ui.presentation

import mnstudio.apps.mnstreamx.domain.model.Channel
import java.util.Locale

/** One catalog per browser session. Filtering/hiding/sorting never renumbers the surviving families. */
internal class LiveChannelNumbers {
    private var source: List<Channel>? = null
    private var cached: Map<String, Int> = emptyMap()
    @Synchronized
    fun forCatalog(channels: List<Channel>, metadata: LiveChannelMetadataCache = LiveChannelMetadataCache(),
        checkpoint: () -> Unit = {}): Map<String, Int> {
        checkpoint()
        if (source === channels) return cached
        val result = LiveChannelRows.project(channels, LiveChannelSort.PROVIDER, Locale.ROOT, metadata, checkpoint)
            .mapIndexed { index, row -> row.preferenceKey to index + 1 }.toMap()
        source = channels; cached = result
        return result
    }
    fun apply(rows: List<LiveChannelRow>, numbers: Map<String, Int>): List<LiveChannelRow> =
        rows.map { it.copy(number = numbers[it.preferenceKey] ?: 0) }
}