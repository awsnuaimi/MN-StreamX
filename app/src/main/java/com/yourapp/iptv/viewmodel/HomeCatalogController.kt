package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.domain.repository.HomeStartupRepository
import com.yourapp.iptv.domain.repository.PlaylistRepository
import com.yourapp.iptv.util.LoadTimings
import kotlinx.coroutines.*

/** UI-scope owner of catalog reads, deferred refreshes and optional startup-cache writes. */
internal class HomeCatalogController(private val scope: CoroutineScope,
    private val playlists: PlaylistRepository, private val startup: HomeStartupRepository,
    private val background: CoroutineDispatcher, private val target: Target,
    private val clock: () -> Long = System::currentTimeMillis) {
    interface Target {
        val hasChannels: Boolean
        fun loading()
        fun restore(snapshot: HomeStartupSnapshot): Boolean
        fun channels(value: List<Channel>, refreshing: Boolean)
        fun failed(error: Exception)
    }

    private var source: AppSettings? = null
    private var generation = 0L
    @Volatile private var ownerEpoch = 0L
    private var loadJob: Job? = null
    private var writeJob: Job? = null
    private var readyJob: Job? = null
    private var pendingRefresh = false
    private var refreshedAt = 0L
    private var restoring = false
    var isRefreshing = false
        private set

    /** Also used for source-read failure. No deferred work may survive an unbound owner. */
    fun bind(value: AppSettings?) {
        generation++;ownerEpoch++
        loadJob?.cancel();writeJob?.cancel();readyJob?.cancel()
        loadJob = null;writeJob = null;readyJob = null
        source = value;pendingRefresh = false;refreshedAt = 0L
        restoring = false;isRefreshing = false
        if (value?.hasSelectedSource() == true) load(value,true)
    }

    /** False asks the ViewModel to retry reading unavailable settings. */
    fun refresh(): Boolean {
        val current = source ?: return false
        if (loadJob?.isActive == true) return true
        pendingRefresh = false;readyJob?.cancel();readyJob = null
        if (current.hasSelectedSource()) load(current,false)
        return true
    }

    fun onHomeReady() {
        if (!pendingRefresh || readyJob?.isActive == true) return
        val current = source ?: return
        val ticket = generation
        val opening = loadJob
        readyJob = scope.launch {
            opening?.join()
            if (isCurrent(ticket) && source == current && pendingRefresh) {
                pendingRefresh = false
                load(current,false)
            }
        }
    }

    fun save(config: OrganizationConfig, catalog: OrganizedChannels) {
        val current = source ?: return
        if (restoring || !current.hasSelectedSource()) return
        val owner = ownerEpoch
        val verifiedAt = refreshedAt
        writeJob?.cancel()
        writeJob = scope.launch(background) {
            currentCoroutineContext().ensureActive()
            if (owner != ownerEpoch) return@launch
            try { startup.save(current,config,catalog,verifiedAt) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Room is authoritative; an optional cache write cannot break playback. */ }
        }
    }

    private suspend fun isCurrent(ticket: Long): Boolean {
        currentCoroutineContext().ensureActive()
        return ticket == generation
    }

    private fun load(value: AppSettings, useStartup: Boolean) {
        val ticket = ++generation
        pendingRefresh = false;isRefreshing = true
        target.loading()
        loadJob = scope.launch {
            try {
                if (!target.hasChannels) {
                    if (useStartup) {
                        val saved = try { startup.restore(value) }
                            catch (error: CancellationException) { throw error }
                            catch (_: Exception) { null }
                        if (!isCurrent(ticket)) return@launch
                        if (saved != null) {
                            refreshedAt = saved.refreshedAtMillis;isRefreshing = false
                            restoring = true
                            val adopted = try { target.restore(saved) } finally { restoring = false }
                            if (!isCurrent(ticket)) return@launch
                            if (adopted) {
                                pendingRefresh = HomeStartupPolicy.needsRefresh(refreshedAt,clock())
                                return@launch
                            }
                            refreshedAt = 0L;isRefreshing = true
                        }
                    }
                    val cached = try { playlists.cachedPlaylist(value) }
                        catch (error: CancellationException) { throw error }
                        catch (_: Exception) { null }
                    if (!isCurrent(ticket)) return@launch
                    cached?.channels?.takeIf { it.isNotEmpty() }?.let { target.channels(it,isRefreshing) }
                }
                val rows = LoadTimings.measure(LoadTimings.Stage.REFRESH) {
                    withContext(background) {
                        when (value.source) {
                            PlaylistSource.M3U -> playlists.loadPlaylist(value.m3uUrl)
                            PlaylistSource.XTREAM -> playlists.loadXtream(value.xtreamServer,
                                value.xtreamUsername,value.xtreamPassword)
                        }.channels
                    }
                }
                if (!isCurrent(ticket)) return@launch
                refreshedAt = clock();isRefreshing = false
                target.channels(rows,isRefreshing)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (!isCurrent(ticket)) return@launch
                isRefreshing = false;target.failed(error)
            }
        }
    }
}