package com.yourapp.iptv.data.parental

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.*
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.flow.combine

internal class ProtectedVod(private val raw: VodRepository, private val parental: ParentalControl) : VodRepository {
    override fun supports(settings: AppSettings) = raw.supports(settings)
    override suspend fun catalog(settings: AppSettings, kind: VodKind): VodCatalog {
        if (!parental.state.value.allows(kind)) return VodCatalog(emptyList(),emptyList())
        val value = raw.catalog(settings,kind)
        return if (parental.state.value.allows(kind)) value else VodCatalog(emptyList(),emptyList())
    }
    override suspend fun category(settings: AppSettings, kind: VodKind, categoryId: String): List<VodItem> {
        if (!parental.state.value.allows(kind)) return emptyList()
        val value = raw.category(settings,kind,categoryId)
        return if (parental.state.value.allows(kind)) value else emptyList()
    }
    override suspend fun details(settings: AppSettings, kind: VodKind, item: VodItem): VodDetails {
        requireAccess(kind)
        return raw.details(settings,kind,item).also { requireAccess(kind) }
    }
    override fun playbackUrl(settings: AppSettings, kind: VodKind, id: String, extension: String): String {
        requireAccess(kind); return raw.playbackUrl(settings,kind,id,extension)
    }
    private fun requireAccess(kind: VodKind) { if (!parental.state.value.allows(kind)) throw ParentalDenied() }
}

internal class ProtectedProgress(private val raw: VodProgressRepository, private val parental: ParentalControl) : VodProgressRepository {
    override fun observe(sourceKey: String) = combine(raw.observe(sourceKey),parental.state) { rows, access ->
        rows.filter { access.allows(it.kind) }
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