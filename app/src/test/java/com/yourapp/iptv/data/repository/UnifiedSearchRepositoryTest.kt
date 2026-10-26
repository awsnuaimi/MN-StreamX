package com.yourapp.iptv.data.repository

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import com.yourapp.iptv.testing.searchAccount
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnifiedSearchRepositoryTest {
    private class Channels : ChannelRepository {
        var account: AppSettings? = null
        var cached: List<Channel>? = listOf(Channel("42", "Shared", streamUrl = "https://synthetic.invalid/live"))
        override suspend fun getCachedChannels(source: AppSettings): List<Channel>? { account = source; return cached }
        override suspend fun getChannels(): List<Channel> = error("Unscoped reads are forbidden")
        override suspend fun getChannel(id: String): Channel? = error("Unexpected lookup")
        override suspend fun loadFromM3u(url: String): List<Channel> = error("Unexpected import")
        override suspend fun loadFromXtream(serverUrl: String, username: String, password: String): List<Channel> = error("Unexpected import")
        override suspend fun search(query: String): List<Channel> = error("Unscoped search is forbidden")
        override suspend fun getFavorites(): List<Channel> = error("Unexpected favorites read")
        override suspend fun addFavorite(channel: Channel) = error("Unexpected mutation")
        override suspend fun removeFavorite(channel: Channel) = error("Unexpected mutation")
    }
    private class Vod : VodRepository {
        var catalogBlock: suspend () -> VodCatalog = { VodCatalog(emptyList(), listOf(VodItem("42", "Shared", "1", plot = "Large plot"))) }
        var categoryBlock: suspend (String) -> List<VodItem> = { listOf(VodItem("42", "Shared $it", it, plot = "Large plot"), VodItem("43", "Other", it)) }
        override fun supports(settings: AppSettings) = settings.source == PlaylistSource.XTREAM
        override suspend fun catalog(settings: AppSettings, kind: VodKind) = catalogBlock()
        override suspend fun category(settings: AppSettings, kind: VodKind, categoryId: String) = categoryBlock(categoryId)
        override suspend fun details(settings: AppSettings, kind: VodKind, item: VodItem) = VodDetails(item.copy(plot = "Fresh details"))
        override fun playbackUrl(settings: AppSettings, kind: VodKind, id: String, extension: String) = error("Search never resolves playback URLs")
    }
    @Test fun channelsUseTheSourceCheckedCacheAndNeverFallBackToAnotherAccountsGlobalRows() = runTest {
        val channels = Channels()
        val repo = UnifiedSearchRepositoryImpl(channels, Vod(), StandardTestDispatcher(testScheduler))
        val account = searchAccount("A")
        assertEquals("42", repo.channels(account)!!.find("shared").items.single().id)
        assertEquals(account, channels.account)
        channels.cached = null
        assertNull(repo.channels(searchAccount("B")))
    }
    @Test fun catalogDownloadsAreSerializedAndSearchIndexesDoNotRetainLargePlotMetadata() = runTest {
        val vod = Vod()
        var active = 0; var maximum = 0
        vod.catalogBlock = {
            active++; maximum = maxOf(maximum, active)
            delay(10); active--
            VodCatalog(emptyList(), listOf(VodItem("42", "Shared", "1", plot = "Large plot")))
        }
        val repo = UnifiedSearchRepositoryImpl(Channels(), vod, StandardTestDispatcher(testScheduler))
        val catalogs = listOf(async { repo.catalog(searchAccount("A"), VodKind.MOVIE) },
            async { repo.catalog(searchAccount("A"), VodKind.SERIES) }).awaitAll()
        assertEquals(1, maximum)
        assertTrue(catalogs.all { it.index.find("shared").items.single().plot == null })
    }
    @Test fun categorySearchRetainsOnlyMatchesAndDetailsAreNotBlockedBehindACatalogDownload() = runTest {
        val vod = Vod()
        val gate = CompletableDeferred<Unit>()
        vod.catalogBlock = { gate.await(); VodCatalog(emptyList(), emptyList()) }
        val repo = UnifiedSearchRepositoryImpl(Channels(), vod, StandardTestDispatcher(testScheduler))
        val loading = async { repo.catalog(searchAccount("A"), VodKind.MOVIE) }
        runCurrent()
        val details = repo.details(searchAccount("A"), VodKind.MOVIE, VodItem("42", "Shared", "1"))
        assertEquals("Fresh details", details.item.plot)
        gate.complete(Unit); loading.await()
        val matches = repo.category(searchAccount("A"), VodKind.MOVIE, "7", "shared")
        assertEquals(listOf("42"), matches.items.map { it.id }); assertNull(matches.items.single().plot)
    }
}