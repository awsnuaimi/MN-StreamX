package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.*

interface VodRepository {
    suspend fun catalog(settings: AppSettings, kind: VodKind): VodCatalog
    suspend fun details(settings: AppSettings, kind: VodKind, item: VodItem): VodDetails
    fun playbackUrl(settings: AppSettings, kind: VodKind, id: String, extension: String): String
}