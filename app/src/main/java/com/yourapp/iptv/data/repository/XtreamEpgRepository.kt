package com.yourapp.iptv.data.repository

import com.yourapp.iptv.data.source.epg.XtreamEpgCache
import com.yourapp.iptv.data.source.xtream.XtreamEpgRequest
import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.EpgSchedule
import com.yourapp.iptv.domain.model.PlaylistLoadException
import com.yourapp.iptv.domain.model.Program
import com.yourapp.iptv.domain.repository.LiveEpgRepository
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
    private val mutex = Mutex()

    override suspend fun load(channel: Channel, settings: AppSettings): EpgSchedule? = withContext(io) {
        val request = XtreamEpgRequest.create(channel, settings) ?: return@withContext null
        mutex.withLock {
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