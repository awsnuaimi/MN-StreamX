package com.yourapp.iptv.data.repository

import com.yourapp.iptv.di.DefaultDispatcher
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import com.yourapp.iptv.domain.search.*
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class UnifiedSearchRepositoryImpl @Inject constructor(
    private val channels: ChannelRepository,
    private val vod: VodRepository,
    @DefaultDispatcher private val background: CoroutineDispatcher
) : UnifiedSearchRepository {
    private val catalogLock = Mutex()
    override fun supportsVod(source: AppSettings) = vod.supports(source)

    override suspend fun channels(source: AppSettings): SearchIndex<Channel>? {
        val cached = channels.getCachedChannels(source) ?: return null
        return withContext(background) { SearchIndex.create(cached, Channel::id, Channel::name) }
    }

    override suspend fun catalog(source: AppSettings, kind: VodKind): SearchVodCatalog = catalogLock.withLock {
        val catalog = vod.catalog(source, kind)
        withContext(background) {
            val compact = catalog.items.map(::compact)
            SearchVodCatalog(SearchIndex.create(compact, VodItem::id, VodItem::name),
                catalog.categories.distinctBy { it.id }, catalog.categoryScoped)
        }
    }

    override suspend fun category(source: AppSettings, kind: VodKind, categoryId: String,
        query: String): SearchMatches<VodItem> = catalogLock.withLock {
        val items = vod.category(source, kind, categoryId)
        withContext(background) {
            val index = SearchIndex.create(items.map(::compact), VodItem::id, VodItem::name)
            val matches = index.find(query)
            matches.copy(limited = index.limited)
        }
    }

    override suspend fun details(source: AppSettings, kind: VodKind, item: VodItem) = vod.details(source, kind, item)

    private fun compact(item: VodItem) = VodItem(item.id, item.name, item.categoryId,
        poster = item.poster, extension = item.extension, year = item.year, categoryIds = item.categoryIds)
}