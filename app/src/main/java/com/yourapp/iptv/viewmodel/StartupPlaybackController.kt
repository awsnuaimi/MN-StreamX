package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.profile.ProfileScope

import com.yourapp.iptv.domain.model.PlaylistCacheKey
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

internal data class StartupPlaybackTarget(val channelId: String, val sourceKey: String)

/** One attempt per Activity launch. All entry points are called on the main thread. */
internal class StartupPlaybackController(
    private val scope: CoroutineScope,
    private val preference: StartupPlaybackRepository,
    private val settings: SettingsRepository,
    private val history: WatchHistoryRepository,
    private val channels: ChannelRepository,
    restoredAttempt: Boolean = false,
    private val persistAttempt: () -> Unit = {}
) {
    private var initialized = false
    private var attempted = restoredAttempt
    private var generation = 0L
    private var job: Job? = null

    fun initialize(freshLaunch: Boolean) {
        initialized = true
        if (!freshLaunch) cancel()
    }

    fun onHomeReady(sourceKey: String, onPlay: (StartupPlaybackTarget) -> Unit) {
        if (!initialized || attempted) return
        // Persist before any suspension: Back, recreation and language changes cannot replay it.
        attempted = true
        persistAttempt()
        if (sourceKey.isBlank()) return
        val token = ++generation
        job = scope.launch {
            try {
                // This optional probe never blocks Home and cannot surprise the user much later.
                withTimeoutOrNull(3_000) {
                    if (!preference.observe().first()) return@withTimeoutOrNull
                    val source = settings.observeSettings().first()
                    if (!source.hasSelectedSource() || ProfileScope.of(source) != sourceKey) return@withTimeoutOrNull
                    val last = history.observe(sourceKey).first().firstOrNull() ?: return@withTimeoutOrNull
                    val available = channels.getPlaybackChannels(source, setOf(last.channelId))
                        .any { it.id == last.channelId && it.streamUrl.isNotBlank() }
                    if (!available) return@withTimeoutOrNull
                    if (!preference.observe().first()) return@withTimeoutOrNull
                    val current = settings.observeSettings().first()
                    if (!current.hasSelectedSource() || ProfileScope.of(current) != sourceKey) return@withTimeoutOrNull
                    if (history.observe(sourceKey).first().firstOrNull() != last) return@withTimeoutOrNull
                    currentCoroutineContext().ensureActive()
                    if (token == generation) onPlay(StartupPlaybackTarget(last.channelId, sourceKey))
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* An optional resume failure leaves the usable Home screen visible. */ }
        }
    }

    /** Actual input, leaving Home or pausing the Activity permanently cancels this launch's intent. */
    fun cancel() {
        if (!attempted) {
            attempted = true
            persistAttempt()
        }
        generation++
        job?.cancel()
        job = null
    }
}