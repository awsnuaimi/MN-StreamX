package com.yourapp.iptv.data.repository

import com.yourapp.iptv.data.cache.HomeCatalogFile
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.domain.repository.*
import com.yourapp.iptv.util.LoadTimings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class FileHomeStartupRepository(file: File, private val settings: SettingsRepository,
    private val organization: OrganizationRepository) : HomeStartupRepository {
    private val storage = HomeCatalogFile(file)
    private val lock = Mutex()

    override suspend fun restore(source: AppSettings): HomeStartupSnapshot? = try {
        LoadTimings.measure(LoadTimings.Stage.CATALOG_READ) {
            withContext(Dispatchers.IO) {
                lock.withLock {
                    if (!source.hasSelectedSource() || !isCurrent(source)) return@withLock null
                    val config = organization.observe().first()
                    if (config.sourceKey != OrganizationNames.sourceKey(source)) return@withLock null
                    val worker = currentCoroutineContext()
                    val restored = storage.read(PlaylistCacheKey.of(source), config) { worker.ensureActive() }
                    if (isCurrent(source) && organization.observe().first() == config) restored else null
                }
            }
        }
    } catch (error: CancellationException) { throw error }
      catch (_: Exception) { null }

    override suspend fun save(source: AppSettings, config: OrganizationConfig,
        catalog: OrganizedChannels, refreshedAt: Long) = withContext(Dispatchers.IO) {
        if (catalog.channels.isEmpty() || catalog.tree.readFailed) return@withContext
        lock.withLock {
            if (!isCurrent(source) || organization.observe().first() != config ||
                config.sourceKey != OrganizationNames.sourceKey(source)) return@withLock
            val worker = currentCoroutineContext()
            storage.write(PlaylistCacheKey.of(source), config, catalog, refreshedAt) { worker.ensureActive() }
        }
    }

    private suspend fun isCurrent(source: AppSettings) =
        PlaylistCacheKey.of(settings.observeSettings().first()) == PlaylistCacheKey.of(source)
}