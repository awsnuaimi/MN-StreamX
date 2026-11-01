package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** A cache hit can be rendered while the provider refreshes in the same collection. */
data class VodLoad<out T>(
    val value: T,
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false
)

interface VodRepository {
    fun supports(settings: AppSettings): Boolean
    suspend fun catalog(settings: AppSettings, kind: VodKind): VodCatalog
    /** Explicit refresh bypasses optional cached snapshots. */
    suspend fun refreshCatalog(settings: AppSettings, kind: VodKind): VodCatalog = catalog(settings, kind)
    suspend fun category(settings: AppSettings, kind: VodKind, categoryId: String): List<VodItem>
    suspend fun refreshCategory(settings: AppSettings, kind: VodKind, categoryId: String): List<VodItem> =
        category(settings, kind, categoryId)

    /** Defaults retain compatibility for fake and non-caching repositories. */
    fun catalogUpdates(settings: AppSettings, kind: VodKind, force: Boolean = false): Flow<VodLoad<VodCatalog>> = flow {
        emit(VodLoad(if (force) refreshCatalog(settings, kind) else catalog(settings, kind)))
    }

    fun categoryUpdates(settings: AppSettings, kind: VodKind, categoryId: String,
        force: Boolean = false): Flow<VodLoad<List<VodItem>>> = flow {
        emit(VodLoad(if (force) refreshCategory(settings, kind, categoryId)
            else category(settings, kind, categoryId)))
    }

    suspend fun details(settings: AppSettings, kind: VodKind, item: VodItem): VodDetails
    fun playbackUrl(settings: AppSettings, kind: VodKind, id: String, extension: String): String
}