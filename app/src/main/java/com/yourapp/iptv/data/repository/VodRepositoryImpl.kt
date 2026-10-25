package com.yourapp.iptv.data.repository

import com.yourapp.iptv.data.source.xtream.XtreamVodDataSource
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.VodRepository
import javax.inject.Inject

class VodRepositoryImpl @Inject constructor() : VodRepository {
    private val source = XtreamVodDataSource()
    override fun supports(settings: AppSettings) = source.supports(settings)
    override suspend fun catalog(settings: AppSettings, kind: VodKind) = source.catalog(settings, kind)
    override suspend fun category(settings: AppSettings, kind: VodKind, categoryId: String) =
        source.category(settings, kind, categoryId)
    override suspend fun details(settings: AppSettings, kind: VodKind, item: VodItem) = source.details(settings, kind, item)
    override fun playbackUrl(settings: AppSettings, kind: VodKind, id: String, extension: String) =
        source.playbackUrl(settings, kind, id, extension)
}