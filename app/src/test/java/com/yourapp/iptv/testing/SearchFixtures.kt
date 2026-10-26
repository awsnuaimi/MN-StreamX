package com.yourapp.iptv.testing

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import com.yourapp.iptv.domain.search.*
import kotlinx.coroutines.flow.*
import java.io.IOException

internal fun searchAccount(name: String) = AppSettings(source = PlaylistSource.XTREAM,
    xtreamServer = "https://synthetic.invalid", xtreamUsername = name, xtreamPassword = "synthetic")

internal class SearchAccounts : SettingsRepository {
    val values = MutableStateFlow(searchAccount("A"))
    override fun observeSettings() = values
    override suspend fun saveSettings(settings: AppSettings) { values.value = settings }
    val key get() = PlaylistCacheKey.of(values.value)
}

internal class SearchHistoryMemory : SearchHistoryRepository {
    val values = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    var fail = false
    var writes = 0
    override fun observe(sourceKey: String) = values.map { it[sourceKey].orEmpty() }
    override suspend fun record(sourceKey: String, query: String) {
        if (fail) throw IOException("Synthetic history failure")
        writes++
        values.value = values.value + (sourceKey to (listOf(query) + values.value[sourceKey].orEmpty().filterNot { it == query }).take(20))
    }
    override suspend fun clear(sourceKey: String) { values.value = values.value - sourceKey }
}

internal class SearchCatalogMemory : UnifiedSearchRepository {
    val loads = mutableListOf<Pair<String, VodKind>>()
    val categoryRequests = mutableListOf<String>()
    var catalogBlock: suspend (AppSettings, VodKind) -> SearchVodCatalog = { account, kind ->
        val rows = listOf(VodItem("42", "Shared ${kind.name} ${account.xtreamUsername}", "1"))
        SearchVodCatalog(SearchIndex.create(rows, VodItem::id, VodItem::name), emptyList(), false)
    }
    var channelsBlock: suspend (AppSettings) -> SearchIndex<Channel>? = { account ->
        SearchIndex.create(listOf(Channel("42", "Shared ${account.xtreamUsername}",
            streamUrl = "https://synthetic.invalid/${account.xtreamUsername}/42")), Channel::id, Channel::name)
    }
    var categoryBlock: suspend (String, String) -> SearchMatches<VodItem> = { id, _ ->
        SearchMatches(listOf(VodItem(id, "Shared $id", id)))
    }
    var detailsBlock: suspend (AppSettings, VodKind, VodItem) -> VodDetails = { _, kind, item ->
        VodDetails(item, if (kind == VodKind.SERIES) listOf(VodSeason(1, "One",
            listOf(VodEpisode("99", "Episode", 1, 1, "mp4")))) else emptyList())
    }
    override fun supportsVod(source: AppSettings) = source.source == PlaylistSource.XTREAM
    override suspend fun channels(source: AppSettings) = channelsBlock(source)
    override suspend fun catalog(source: AppSettings, kind: VodKind): SearchVodCatalog {
        loads.add(source.xtreamUsername to kind)
        return catalogBlock(source, kind)
    }
    override suspend fun category(source: AppSettings, kind: VodKind, categoryId: String, query: String): SearchMatches<VodItem> {
        categoryRequests.add(categoryId)
        return categoryBlock(categoryId, query)
    }
    override suspend fun details(source: AppSettings, kind: VodKind, item: VodItem) = detailsBlock(source, kind, item)

    suspend fun scoped(count: Int) = SearchVodCatalog(SearchIndex.create(emptyList<VodItem>(), VodItem::id, VodItem::name),
        (1..count).map { VodCategory(it.toString(), "Category $it") }, true)
}