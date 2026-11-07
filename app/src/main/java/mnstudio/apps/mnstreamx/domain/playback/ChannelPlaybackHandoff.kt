package mnstudio.apps.mnstreamx.domain.playback

import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.organization.OrganizationConfig
import mnstudio.apps.mnstreamx.domain.organization.OrganizedChannels
import javax.inject.Inject
import javax.inject.Singleton

data class PreparedLiveQueue(val config: OrganizationConfig, val catalog: OrganizedChannels)

/** One click, one consumer, one profile/source/access epoch. Never persisted or put in a route. */
@Singleton
class ChannelPlaybackHandoff internal constructor(private val clock: () -> Long) {
    @Inject constructor() : this({ System.nanoTime() / 1_000_000 })
    data class Selection(val channel: Channel, val queue: PreparedLiveQueue?)
    private data class Entry(val scope: String, val selection: Selection, val access: Long, val at: Long)
    private var entry: Entry? = null

    @Synchronized fun offer(scope: String, channel: Channel, access: Long, queue: PreparedLiveQueue? = null) {
        entry = Entry(scope, Selection(channel,queue), access, clock())
    }

    @Synchronized fun take(scope: String?, id: String?, access: Long): Channel? {
        return takeSelection(scope,id,access)?.channel
    }

    @Synchronized fun takeSelection(scope: String?, id: String?, access: Long): Selection? {
        val saved = entry
        entry = null
        return saved?.takeIf { it.scope == scope && it.selection.channel.id == id && it.access == access &&
            clock() - it.at in 0..5_000 }?.selection
    }
}