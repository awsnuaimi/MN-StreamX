package mnstudio.apps.mnstreamx.data.repository

import mnstudio.apps.mnstreamx.data.source.epg.XtreamEpgCache
import mnstudio.apps.mnstreamx.data.source.xtream.XtreamEpgRequest
import mnstudio.apps.mnstreamx.domain.model.AppSettings
import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.model.EpgSchedule
import mnstudio.apps.mnstreamx.domain.model.PlaylistLoadException
import mnstudio.apps.mnstreamx.domain.model.Program
import mnstudio.apps.mnstreamx.domain.repository.LiveEpgRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import kotlin.coroutines.coroutineContext

internal class XtreamEpgRepository(
    private val cache: XtreamEpgCache,
    private val fetch: suspend (HttpUrl) -> List<Program>,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : LiveEpgRepository {
    // Bounded lock striping keeps requests for the same channel/cache key atomic,
    // without serializing unrelated EPG network calls behind a single slow server.
    // Fixed size avoids accumulating one Mutex per channel in large IPTV catalogs.
    private val cacheLocks = Array(16) { Mutex() }

    private fun lockFor(key: String): Mutex = cacheLocks[(key.hashCode() and Int.MAX_VALUE) % cacheLocks.size]

    override suspend fun load(channel: Channel, settings: AppSettings): EpgSchedule? = withContext(io) {
        val request = XtreamEpgRequest.create(channel, settings) ?: return@withContext null
        lockFor(request.cacheKey).withLock {
            coroutineContext.ensureActive()
            val saved = cache.read(request.cacheKey, now())
            if (saved?.isFresh(now()) == true) return@withLock EpgSchedule(saved.programs)
            try {
                val programs = fetch(request.url)
                coroutineContext.ensureActive()
                // A cache write failure must not discard a valid network response.
                runCatching { cache.write(request.cacheKey, XtreamEpgCache.Entry(now(), programs)) }
                EpgSchedule(programs)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (saved != null) EpgSchedule(saved.programs, isStale = true)
                else throw PlaylistLoadException("تعذر تحديث دليل البرامج. ستتم إعادة المحاولة تلقائيًا.")
            }
        }
    }
}