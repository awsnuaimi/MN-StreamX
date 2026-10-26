package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.search.*

interface UnifiedSearchRepository {
    fun supportsVod(source: AppSettings): Boolean
    suspend fun channels(source: AppSettings): SearchIndex<Channel>?
    suspend fun catalog(source: AppSettings, kind: VodKind): SearchVodCatalog
    suspend fun category(source: AppSettings, kind: VodKind, categoryId: String, query: String): SearchMatches<VodItem>
    suspend fun details(source: AppSettings, kind: VodKind, item: VodItem): VodDetails
}