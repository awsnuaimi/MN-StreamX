package mnstudio.apps.mnstreamx.domain.playback

import mnstudio.apps.mnstreamx.domain.model.Channel
import javax.inject.Inject
import javax.inject.Singleton

/** One click, one consumer, one profile/source/access epoch. Never persisted or put in a route. */
@Singleton
class ChannelPlaybackHandoff internal constructor(private val clock: () -> Long) {
    @Inject constructor() : this({ System.nanoTime() / 1_000_000 })
    private data class Entry(val scope: String, val channel: Channel, val access: Long, val at: Long)
    private var entry: Entry? = null

    @Synchronized fun offer(scope: String, channel: Channel, access: Long) {
        entry = Entry(scope, channel, access, clock())
    }

    @Synchronized fun take(scope: String?, id: String?, access: Long): Channel? {
        val saved = entry
        entry = null
        return saved?.takeIf { it.scope == scope && it.channel.id == id && it.access == access &&
            clock() - it.at in 0..5_000 }?.channel
    }
}