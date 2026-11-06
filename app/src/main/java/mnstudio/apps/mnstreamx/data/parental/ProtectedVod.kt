package mnstudio.apps.mnstreamx.data.parental

import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.domain.parental.*
import mnstudio.apps.mnstreamx.domain.repository.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect

internal class ProtectedVod(private val raw: VodRepository, private val parental: ParentalControl) : VodRepository {
    override fun supports(settings: AppSettings) = raw.supports(settings)
    override suspend fun catalog(settings: AppSettings, kind: VodKind): VodCatalog {
        if (!parental.state.value.allows(kind)) return VodCatalog(emptyList(),emptyList())
        val value = raw.catalog(settings,kind)
        return if (parental.state.value.allows(kind)) value else VodCatalog(emptyList(),emptyList())
    }
    override suspend fun refreshCatalog(settings: AppSettings, kind: VodKind): VodCatalog {
        if (!parental.state.value.allows(kind)) return VodCatalog(emptyList(), emptyList())
        val value = raw.refreshCatalog(settings, kind)
        return if (parental.state.value.allows(kind)) value else VodCatalog(emptyList(), emptyList())
    }
    override suspend fun category(settings: AppSettings, kind: VodKind, categoryId: String): List<VodItem> {
        if (!parental.state.value.allows(kind)) return emptyList()
        val value = raw.category(settings,kind,categoryId)
        return if (parental.state.value.allows(kind)) value else emptyList()
    }
    override suspend fun refreshCategory(settings: AppSettings, kind: VodKind, categoryId: String): List<VodItem> {
        if (!parental.state.value.allows(kind)) return emptyList()
        val value = raw.refreshCategory(settings, kind, categoryId)
        return if (parental.state.value.allows(kind)) value else emptyList()
    }
    override fun catalogUpdates(settings: AppSettings, kind: VodKind,
        force: Boolean): Flow<VodLoad<VodCatalog>> = flow {
        if (!parental.state.value.allows(kind)) {
            emit(VodLoad(VodCatalog(emptyList(), emptyList())))
            return@flow
        }
        combine(raw.catalogUpdates(settings, kind, force), parental.state) { event, access ->
            event.copy(value = if (access.allows(kind)) event.value else VodCatalog(emptyList(), emptyList()))
        }.collect { emit(it) }
    }

    override fun browseCatalogUpdates(settings: AppSettings, kind: VodKind, force: Boolean): Flow<VodLoad<VodCatalog>> = flow {
        if (!parental.state.value.allows(kind)) {
            emit(VodLoad(VodCatalog(emptyList(), emptyList())))
            return@flow
        }
        combine(raw.browseCatalogUpdates(settings, kind, force), parental.state) { event, access ->
            event.copy(value = if (access.allows(kind)) event.value else VodCatalog(emptyList(), emptyList()))
        }.collect { emit(it) }
    }

    override fun categoryUpdates(settings: AppSettings, kind: VodKind, categoryId: String,
        force: Boolean): Flow<VodLoad<List<VodItem>>> = flow {
        if (!parental.state.value.allows(kind)) {
            emit(VodLoad(emptyList()))
            return@flow
        }
        combine(raw.categoryUpdates(settings, kind, categoryId, force), parental.state) { event, access ->
            event.copy(value = if (access.allows(kind)) event.value else emptyList())
        }.collect { emit(it) }
    }

    override suspend fun details(settings: AppSettings, kind: VodKind, item: VodItem): VodDetails {
        requireAccess(kind)
        return raw.details(settings,kind,item).also { requireAccess(kind) }
    }
    override suspend fun refreshDetails(settings: AppSettings, kind: VodKind, item: VodItem): VodDetails {
        requireAccess(kind)
        return raw.refreshDetails(settings,kind,item).also { requireAccess(kind) }
    }
    override suspend fun refreshPlayback(settings: AppSettings, kind: VodKind, id: String,
        extension: String): VodPlaybackSource {
        requireAccess(kind)
        return raw.refreshPlayback(settings,kind,id,extension).also { requireAccess(kind) }
    }
    override fun playbackUrl(settings: AppSettings, kind: VodKind, id: String, extension: String): String {
        requireAccess(kind); return raw.playbackUrl(settings,kind,id,extension)
    }
    override suspend fun resolvePlayback(settings: AppSettings, kind: VodKind, id: String,
        extension: String): VodPlaybackSource {
        requireAccess(kind)
        return raw.resolvePlayback(settings,kind,id,extension).also { requireAccess(kind) }
    }
    private fun requireAccess(kind: VodKind) { if (!parental.state.value.allows(kind)) throw ParentalDenied() }
}

internal class ProtectedProgress(private val raw: VodProgressRepository, private val parental: ParentalControl) : VodProgressRepository {
    override fun observe(sourceKey: String) = combine(raw.observe(sourceKey),parental.state) { rows, access ->
        rows.filter { access.allows(it.kind) }
    }
    override fun observeCompleted(sourceKey: String) = combine(raw.observeCompleted(sourceKey),parental.state) { rows, access ->
        if (access.allows(VodKind.SERIES)) rows else emptyList()
    }
    override fun record(sourceKey: String, entry: VodProgressEntry, completed: Boolean) = raw.record(sourceKey,entry,completed)
}

internal class ProtectedSearchHistory(private val raw: SearchHistoryRepository, private val parental: ParentalControl) : SearchHistoryRepository {
    override fun observe(sourceKey: String) = combine(raw.observe(sourceKey),parental.state) { rows, access ->
        if (!access.ready || access.storageError || access.contentRestricted) emptyList() else rows
    }
    override suspend fun record(sourceKey: String, query: String) = raw.record(sourceKey,query)
    override suspend fun clear(sourceKey: String) = raw.clear(sourceKey)
}