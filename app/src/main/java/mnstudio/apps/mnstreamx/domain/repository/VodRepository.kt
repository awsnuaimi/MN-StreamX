package mnstudio.apps.mnstreamx.domain.repository

import mnstudio.apps.mnstreamx.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** A cache hit can be rendered while the provider refreshes in the same collection. */
data class VodLoad<out T>(
    val value: T,
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false
)

interface VodRepository {
    fun supports(settings: AppSettings): Boolean
    suspend fun catalog(settings: AppSettings, kind: VodKind): VodCatalog
    suspend fun refreshCatalog(settings: AppSettings, kind: VodKind): VodCatalog = catalog(settings, kind)
    suspend fun category(settings: AppSettings, kind: VodKind, categoryId: String): List<VodItem>
    suspend fun refreshCategory(settings: AppSettings, kind: VodKind, categoryId: String): List<VodItem> =
        category(settings, kind, categoryId)

    fun catalogUpdates(settings: AppSettings, kind: VodKind, force: Boolean = false): Flow<VodLoad<VodCatalog>> = flow {
        emit(VodLoad(if (force) refreshCatalog(settings, kind) else catalog(settings, kind)))
    }

    fun categoryUpdates(settings: AppSettings, kind: VodKind, categoryId: String,
        force: Boolean = false): Flow<VodLoad<List<VodItem>>> = flow {
        emit(VodLoad(if (force) refreshCategory(settings, kind, categoryId)
            else category(settings, kind, categoryId)))
    }

    /** Browsing may load categories first; global search keeps the full catalog contract. */
    fun browseCatalogUpdates(settings: AppSettings, kind: VodKind, force: Boolean = false): Flow<VodLoad<VodCatalog>> =
        catalogUpdates(settings, kind, force)

    suspend fun details(settings: AppSettings, kind: VodKind, item: VodItem): VodDetails
    suspend fun refreshDetails(settings: AppSettings, kind: VodKind, item: VodItem): VodDetails =
        details(settings, kind, item)
    fun playbackUrl(settings: AppSettings, kind: VodKind, id: String, extension: String): String

    suspend fun refreshPlayback(settings: AppSettings, kind: VodKind, id: String,
        extension: String): VodPlaybackSource = resolvePlayback(settings, kind, id, extension)

    suspend fun resolvePlayback(settings: AppSettings, kind: VodKind, id: String,
        extension: String): VodPlaybackSource {
        if (kind != VodKind.MOVIE) return VodPlaybackSource(playbackUrl(settings,kind,id,extension),extension)
        val fresh = try {
            withTimeoutOrNull(10_000L) { details(settings,kind,VodItem(id,id,null,extension = extension)) }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { null }
        currentCoroutineContext().ensureActive()
        val verified = fresh?.takeIf { it.item.id == id }
        return verified?.playback ?: (verified?.item?.extension ?: extension).let { actual ->
            VodPlaybackSource(playbackUrl(settings,kind,id,actual),actual)
        }
    }
}