package mnstudio.apps.mnstreamx.data.repository

import android.content.Context
import mnstudio.apps.mnstreamx.data.cache.VodCatalogCachePolicy
import mnstudio.apps.mnstreamx.data.cache.VodCatalogSnapshot
import mnstudio.apps.mnstreamx.data.cache.VodSnapshotWriteQueue
import mnstudio.apps.mnstreamx.data.source.xtream.XtreamVodDataSource
import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.domain.performance.PerformanceJournal
import mnstudio.apps.mnstreamx.domain.performance.PerformanceStage
import mnstudio.apps.mnstreamx.domain.repository.VodLoad
import mnstudio.apps.mnstreamx.domain.repository.VodRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Cached below ProtectedVod: all emissions still go through parental permissions. */
@Singleton
class VodRepositoryImpl @Inject constructor(@ApplicationContext context: Context) : VodRepository {
    private val source = XtreamVodDataSource()
    private val disk = VodCatalogSnapshot(context.cacheDir.resolve("vod_catalog_v1"))
    private var persistSnapshot: suspend (String, VodCatalogSnapshot.Saved) -> Unit = { key, saved ->
        disk.write(key, saved.catalog, saved.writtenAtMs)
    }
    private val writeQueue = VodSnapshotWriteQueue(persist = { key, saved -> persistSnapshot(key, saved) })
    private val memory = LinkedHashMap<String, VodCatalogSnapshot.Saved>(8, .75f, true)
    private val lock = Any()
    private data class Gate(val mutex: Mutex = Mutex(), var users: Int = 0)
    private val gates = HashMap<String, Gate>()
    private var network: suspend (AppSettings, VodKind, String?) -> VodCatalog = { account, kind, category ->
        if (category == null) source.catalog(account, kind)
        else VodCatalog(emptyList(), source.category(account, kind, category))
    }

    /** Test seam that exercises the real disk/cache/coalescing code without provider access. */
    internal constructor(context: Context, fetch: suspend (AppSettings, VodKind, String?) -> VodCatalog) : this(context) {
        network = fetch
    }

    /** Allows a blocking synthetic cache writer to verify publication ordering. */
    internal constructor(context: Context, fetch: suspend (AppSettings, VodKind, String?) -> VodCatalog,
        persist: suspend (String, VodCatalogSnapshot.Saved) -> Unit) : this(context, fetch) {
        persistSnapshot = persist
    }

    override fun supports(settings: AppSettings) = source.supports(settings)

    override suspend fun catalog(settings: AppSettings, kind: VodKind): VodCatalog =
        catalogUpdates(settings, kind).last().value

    override suspend fun refreshCatalog(settings: AppSettings, kind: VodKind): VodCatalog =
        catalogUpdates(settings, kind, force = true).last().value

    override suspend fun category(settings: AppSettings, kind: VodKind, categoryId: String): List<VodItem> =
        categoryUpdates(settings, kind, categoryId).last().value

    override suspend fun refreshCategory(settings: AppSettings, kind: VodKind, categoryId: String): List<VodItem> =
        categoryUpdates(settings, kind, categoryId, force = true).last().value

    override fun catalogUpdates(settings: AppSettings, kind: VodKind, force: Boolean): Flow<VodLoad<VodCatalog>> =
        changes(settings, kind, null, force)

    override fun categoryUpdates(settings: AppSettings, kind: VodKind, categoryId: String,
        force: Boolean): Flow<VodLoad<List<VodItem>>> =
        changes(settings, kind, categoryId, force).map { event ->
            VodLoad(event.value.items, event.refreshing, event.refreshFailed)
        }

    private fun changes(settings: AppSettings, kind: VodKind, category: String?,
        force: Boolean): Flow<VodLoad<VodCatalog>> = flow {
        val key = VodCatalogCachePolicy.key(PlaylistCacheKey.of(settings), kind.name, category)
        val cached = if (force) null else read(key)
        val now = System.currentTimeMillis()
        val usable = cached?.takeIf { VodCatalogCachePolicy.usableOffline(it.writtenAtMs, now) }
        val fresh = usable != null && VodCatalogCachePolicy.fresh(usable.writtenAtMs, now)
        if (usable != null) {
            emit(VodLoad(usable.catalog, refreshing = !fresh))
            if (fresh) return@flow
        }
        try {
            val latest = fetch(settings, kind, category, key, force)
            emit(VodLoad(latest))
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (usable == null) throw error
            // Keep cached content visible; do not present a network failure as an empty library.
            emit(VodLoad(usable.catalog, refreshFailed = true))
        }
    }

    private suspend fun fetch(settings: AppSettings, kind: VodKind, category: String?,
        key: String, force: Boolean): VodCatalog = gated(key) {
        // Waiting callers reuse the fresh result rather than making identical provider requests.
        if (!force) read(key)?.takeIf {
            VodCatalogCachePolicy.fresh(it.writtenAtMs, System.currentTimeMillis())
        }?.let { return@gated it.catalog }
        val latest = PerformanceJournal.measure(PerformanceStage.VOD_PROVIDER_FETCH) {
            network(settings, kind, category)
        }
        val saved = VodCatalogSnapshot.Saved(latest, System.currentTimeMillis())
        remember(key, saved)
        // Publishing is independent of slow gzip/disk writes. The single bounded
        // worker persists snapshots in order, including after the screen closes.
        writeQueue.offer(key, saved)
        latest
    }

    private suspend fun <T> gated(key: String, block: suspend () -> T): T {
        val gate = synchronized(lock) {
            gates.getOrPut(key) { Gate() }.also { it.users++ }
        }
        try { return gate.mutex.withLock { block() } }
        finally {
            synchronized(lock) {
                gate.users--
                if (gate.users == 0 && gates[key] === gate) gates.remove(key)
            }
        }
    }

    private suspend fun read(key: String): VodCatalogSnapshot.Saved? =
        PerformanceJournal.measure(PerformanceStage.VOD_CACHE_READ) {
            val inMemory = PerformanceJournal.measure(PerformanceStage.VOD_CACHE_MEMORY_LOOKUP) {
                synchronized(lock) { memory[key] }
            }
            inMemory?.let { return@measure it }
            val saved = try {
                PerformanceJournal.measure(PerformanceStage.VOD_CACHE_DISK_LOAD) {
                    withContext(Dispatchers.IO) { disk.read(key) }
                }
            }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { null } ?: return@measure null
            remember(key, saved)
            saved
        }

    private fun remember(key: String, saved: VodCatalogSnapshot.Saved) = synchronized(lock) {
        memory[key] = saved
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