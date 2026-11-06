package mnstudio.apps.mnstreamx.viewmodel

import mnstudio.apps.mnstreamx.domain.profile.ProfileScope

import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.domain.repository.WatchHistoryRepository

/** Receives only the renderer-confirmed callback, never selection or focus events. */
internal class PlaybackHistoryRecorder(private val history: WatchHistoryRepository,
    private val current: () -> Channel?) {
    var sourceKey: String? = null
        private set
    private var lastTime = 0L
    fun bind(source: AppSettings?) { sourceKey = source?.takeIf { it.hasSelectedSource() }?.let(ProfileScope::of) }
    fun onFirstFrame(selected: Channel) {
        val key = sourceKey ?: return
        val active = current() ?: return
        if (active.id != selected.id || active.streamUrl != selected.streamUrl) return
        lastTime = maxOf(System.currentTimeMillis(), lastTime + 1)
        try { history.recordStarted(key, WatchHistoryEntry(selected.id, lastTime)) }
        catch (_: Exception) { /* Optional history must never interrupt renderer callbacks. */ }
    }
}