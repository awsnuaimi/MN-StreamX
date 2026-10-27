package com.yourapp.iptv.data.parental

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.*
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.domain.repository.HomeStartupRepository
import com.yourapp.iptv.domain.repository.ChannelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class ProtectedHomeStartup(private val raw: HomeStartupRepository,
    private val parental: ParentalControl, private val unfiltered: ChannelRepository? = null) : HomeStartupRepository {
    override suspend fun restore(source: AppSettings): HomeStartupSnapshot? {
        val saved = raw.restore(source) ?: return null
        return withContext(Dispatchers.Default) {
            val access = parental.state.value
            if (!access.ready || access.storageError) return@withContext null
            val key = PlaylistCacheKey.of(source)
            val rows = saved.catalog.channels.filter { access.allows(key,it) }
            val ids = rows.mapTo(HashSet()) { it.id }
            val groups = rows.groupingBy { it.group }.eachCount()
            val catalog = saved.catalog.copy(channels = rows,tree = saved.catalog.tree.withCounts(rows),
                assignments = saved.catalog.assignments.filterKeys { it in ids },
                groups = saved.catalog.groups.mapNotNull { group -> groups[group.name]?.let { group.copy(count = it) } })
            saved.copy(catalog = ParentalProjection.prune(catalog,access))
        }
    }
    override suspend fun save(source: AppSettings, config: OrganizationConfig, catalog: OrganizedChannels, refreshedAt: Long) {
        val access = parental.state.value
        if (!access.allows(ParentalSection.LIVE)) return
        // Persist a complete private snapshot, never the filtered UI projection.
        if (!access.configured) { raw.save(source,config,catalog,refreshedAt); return }
        val complete = unfiltered?.getCachedChannels(source) ?: return
        val restored = withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            OrganizationProjectionCache.Shared.project(complete,config) { context.ensureActive() }
        }
        raw.save(source,config,restored,refreshedAt)
    }
}