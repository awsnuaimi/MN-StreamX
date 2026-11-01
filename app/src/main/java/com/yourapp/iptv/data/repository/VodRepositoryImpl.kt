package com.yourapp.iptv.data.repository

import android.content.Context
import com.yourapp.iptv.data.cache.VodCatalogCachePolicy
import com.yourapp.iptv.data.cache.VodCatalogSnapshot
import com.yourapp.iptv.data.source.xtream.XtreamVodDataSource
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.VodRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Cached below ProtectedVod: every read is still checked against current parental permissions. */
@Singleton
class VodRepositoryImpl @Inject constructor(@ApplicationContext context: Context) : VodRepository {
    private val source = XtreamVodDataSource()
    private val disk = VodCatalogSnapshot(context.cacheDir.resolve("vod_catalog_v1"))
    private val memory = LinkedHashMap<String, VodCatalogSnapshot.Saved>(8, .75f, true)
    private val lock = Any()

    override fun supports(settings: AppSettings) = source.supports(settings)

    override suspend fun catalog(settings: AppSettings, kind: VodKind): VodCatalog =
        load(settings, kind, null, force = false)

    override suspend fun refreshCatalog(settings: AppSettings, kind: VodKind): VodCatalog =
        load(settings, kind, null, force = true)

    override suspend fun category(settings: AppSettings, kind: VodKind, categoryId: String): List<VodItem> =
        load(settings, kind, categoryId, force = false).items

    override suspend fun refreshCategory(settings: AppSettings, kind: VodKind, categoryId: String): List<VodItem> =
        load(settings, kind, categoryId, force = true).items

    private suspend fun load(settings: AppSettings, kind: VodKind, category: String?,
        force: Boolean): VodCatalog {
        val key = VodCatalogCachePolicy.key(PlaylistCacheKey.of(settings), kind.name, category)
        val cached = read(key)
        val now = System.currentTimeMillis()
        if (!force && cached != null && VodCatalogCachePolicy.fresh(cached.writtenAtMs, now)) {
            return cached.catalog
        }
        return try {
            val latest = if (category == null) source.catalog(settings, kind)
                else VodCatalog(emptyList(), source.category(settings, kind, category))
            val saved = VodCatalogSnapshot.Saved(latest, System.currentTimeMillis())
            remember(key, saved)
            // Disk is an optional optimization: a storage problem must not prevent displaying data.
            try { withContext(Dispatchers.IO) { disk.write(key, latest, saved.writtenAtMs) } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { }
            latest
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (!force && cached != null &&
                VodCatalogCachePolicy.usableOffline(cached.writtenAtMs, System.currentTimeMillis())) {
                cached.catalog
            } else throw error
        }
    }

    private suspend fun read(key: String): VodCatalogSnapshot.Saved? {
        synchronized(lock) { memory[key] }?.let { return it }
        val saved = try { withContext(Dispatchers.IO) { disk.read(key) } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { null } ?: return null
        remember(key, saved)
        return saved
    }

    private fun remember(key: String, saved: VodCatalogSnapshot.Saved) = synchronized(lock) {
        memory[key] = saved
        // Keep only a small number of potentially huge Xtream lists resident in RAM.
        while (memory.size > 4 ||
            (memory.size > 1 && memory.values.sumOf { it.catalog.items.size } > 80_000)) {
            memory.remove(memory.keys.first())
        }
    }

    override suspend fun details(settings: AppSettings, kind: VodKind, item: VodItem) =
        source.details(settings, kind, item)

    override fun playbackUrl(settings: AppSettings, kind: VodKind, id: String, extension: String) =
        source.playbackUrl(settings, kind, id, extension)
}