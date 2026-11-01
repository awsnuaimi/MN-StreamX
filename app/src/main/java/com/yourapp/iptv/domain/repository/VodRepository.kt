package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.*

interface VodRepository {
    fun supports(settings: AppSettings): Boolean
    suspend fun catalog(settings: AppSettings, kind: VodKind): VodCatalog
    /** Explicit refresh bypasses optional cached snapshots. */
    suspend fun refreshCatalog(settings: AppSettings, kind: VodKind): VodCatalog = catalog(settings, kind)
    suspend fun category(settings: AppSettings, kind: VodKind, categoryId: String): List<VodItem>
    suspend fun refreshCategory(settings: AppSettings, kind: VodKind, categoryId: String): List<VodItem> =
        category(settings, kind, categoryId)
    suspend fun details(settings: AppSettings, kind: VodKind, item: VodItem): VodDetails
    fun playbackUrl(settings: AppSettings, kind: VodKind, id: String, extension: String): String
}