package mnstudio.apps.mnstreamx.domain.organization

import mnstudio.apps.mnstreamx.domain.model.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One bounded entry shared by home, guide and player. Source identity prevents account mixing. */
internal class OrganizationProjectionCache {
    private data class Entry(val source: List<Channel>, val config: OrganizationConfig,
        val result: OrganizedChannels)
    private val lock = Mutex()
    private var entry: Entry? = null
    private var metadata = OrganizationMetadata()

    suspend fun project(source: List<Channel>, config: OrganizationConfig,
        checkpoint: () -> Unit): OrganizedChannels = lock.withLock {
        checkpoint()
        val previous = entry
        if (previous != null && previous.source === source && previous.config == config) previous.result
        else {
            if (previous?.config?.sourceKey != config.sourceKey) metadata = OrganizationMetadata()
            val refreshed = previous?.takeIf { it.config == config }?.let { refreshValues(it, source, checkpoint) }
            (refreshed ?: ChannelOrganizer.project(source, config, checkpoint, metadata)).also {
            checkpoint()
            entry = Entry(source, config, it)
            }
        }
    }

    /** URL/logo/favorite refreshes keep verified assignments, folder counts and sort order. */
    private fun refreshValues(previous: Entry, source: List<Channel>, checkpoint: () -> Unit): OrganizedChannels? {
        if (source.size != previous.source.size) return null
        val current = HashMap<String, Channel>(source.size)
        source.forEachIndexed { index, channel ->
            checkpoint()
            val old = previous.source[index]
            // Source order also controls generated-folder order; reorder must use the full path.
            if (old.id != channel.id || old.name != channel.name || old.group != channel.group) return null
            current.putIfAbsent(channel.id, channel)
        }
        var changed: MutableList<Channel>? = null
        previous.result.channels.forEachIndexed { index, old ->
            checkpoint()
            val fresh = current.getValue(old.id)
            if (fresh.logoUrl != old.logoUrl || fresh.streamUrl != old.streamUrl || fresh.isFavorite != old.isFavorite) {
                val projected = if (fresh.folderIds == old.folderIds) fresh else fresh.copy(folderIds = old.folderIds)
                val rows = changed ?: previous.result.channels.toMutableList().also { changed = it }
                rows[index] = projected
            }
        }
        checkpoint()
        return changed?.let { previous.result.copy(channels = it) } ?: previous.result
    }

    companion object { val Shared = OrganizationProjectionCache() }
}