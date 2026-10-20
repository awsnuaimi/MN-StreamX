package com.yourapp.iptv.data.repository

import com.yourapp.iptv.data.cache.ChannelSnapshotStore
import com.yourapp.iptv.data.local.dao.*
import com.yourapp.iptv.data.local.entity.*
import com.yourapp.iptv.data.source.m3u.M3uDataSource
import com.yourapp.iptv.data.source.xtream.XtreamDataSource
import com.yourapp.iptv.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ChannelSnapshotTest {
    private val database = Channels()
    private val stamp = Stamp()
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var source: AppSettings

    @Before fun setUp() {
        server = MockWebServer(); server.start(); client = OkHttpClient()
        source = AppSettings(m3uUrl = server.url("/a.m3u").toString())
    }
    @After fun tearDown() {
        client.dispatcher.cancelAll(); client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown(); server.shutdown()
    }

    @Test fun recreatedRepositoryReadsTheLastSnapshotWithoutNetwork() = runBlocking {
        enqueuePlaylist()
        val imported = repository().loadFromM3u(source.m3uUrl)
        val cached = repository().getCachedChannels(source)
        assertEquals(imported, cached)
        assertEquals(1, server.requestCount)
        assertEquals(PlaylistCacheKey.of(source), stamp.value)
    }

    @Test fun legacyRowsAndMismatchedSourcesAreNeverUsedAsCache() = runBlocking {
        assertNull(repository().getCachedChannels(source))
        stamp.value = PlaylistCacheKey.of(source)
        assertNotNull(repository().getCachedChannels(source))
        assertNull(repository().getCachedChannels(source.copy(m3uUrl = server.url("/other.m3u").toString())))
        val account = AppSettings(xtreamServer = "https://unit.test", xtreamUsername = "user",
            xtreamPassword = "first", source = PlaylistSource.XTREAM)
        stamp.value = PlaylistCacheKey.of(account)
        assertNotNull(repository().getCachedChannels(account))
        assertNull(repository().getCachedChannels(account.copy(xtreamPassword = "changed")))
        assertNull(repository().getCachedChannels(account.copy(xtreamUsername = "other")))
        assertNull(repository().getCachedChannels(AppSettings()))
    }

    @Test fun networkFailureKeepsTheValidSnapshot() = runBlocking {
        enqueuePlaylist()
        val repository = repository()
        val original = repository.loadFromM3u(source.m3uUrl)
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(runCatching { repository.loadFromM3u(source.m3uUrl) }.isFailure)
        assertEquals(original, repository.getCachedChannels(source))
    }

    @Test fun failedDatabaseWriteLeavesNoMisattributedCache() = runBlocking {
        stamp.value = PlaylistCacheKey.of(source)
        database.failWrite = true
        enqueuePlaylist()
        assertTrue(runCatching { repository().loadFromM3u(source.m3uUrl) }.isFailure)
        assertNull(stamp.value)
        assertNull(repository().getCachedChannels(source))
    }

    @Test fun failureToInvalidateProvenanceDoesNotOverwriteTheDatabase() = runBlocking {
        stamp.value = PlaylistCacheKey.of(source)
        stamp.failWrite = true
        val previous = database.values
        enqueuePlaylist()
        assertTrue(runCatching { repository().loadFromM3u(source.m3uUrl) }.isFailure)
        assertEquals(previous, database.values)
    }

    @Test fun failureToCommitTheNewStampLeavesTheSnapshotUntrusted() = runBlocking {
        stamp.value = PlaylistCacheKey.of(source)
        stamp.failCommit = true
        enqueuePlaylist()
        assertTrue(runCatching { repository().loadFromM3u(source.m3uUrl) }.isFailure)
        assertNull(stamp.value)
        assertNull(repository().getCachedChannels(source))
    }

    @Test fun cachedReadCannotObserveRowsDuringAReplacement() = runBlocking {
        val repository = repository()
        stamp.value = PlaylistCacheKey.of(source)
        database.gate = CompletableDeferred()
        enqueuePlaylist()
        val importing = async(Dispatchers.Default) { repository.loadFromM3u(source.m3uUrl) }
        withTimeout(5_000) { database.writeStarted.await() }
        val reading = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            repository.getCachedChannels(source)
        }
        assertFalse(reading.isCompleted)
        database.gate!!.complete(Unit)
        assertEquals(importing.await(), reading.await())
    }

    private fun repository() = ChannelRepositoryImpl(M3uDataSource(client), XtreamDataSource(client),
        database, Favorites(), stamp)

    private fun enqueuePlaylist() {
        server.enqueue(MockResponse().setBody("#EXTM3U\n#EXTINF:-1 group-title=\"News\",Alpha\nhttps://unit.test/alpha.ts\n"))
    }

    private class Stamp : ChannelSnapshotStore {
        var value: String? = null
        var failWrite = false
        var failCommit = false
        override suspend fun read() = value
        override suspend fun write(fingerprint: String?) {
            if (failWrite || (failCommit && fingerprint != null)) throw IOException("Cannot save metadata")
            value = fingerprint
        }
    }
    private class Channels : ChannelDao {
        var values = listOf(ChannelEntity("legacy", "Legacy", null, "https://unit.test/old"))
        var failWrite = false
        var gate: CompletableDeferred<Unit>? = null
        val writeStarted = CompletableDeferred<Unit>()
        override suspend fun insertChannels(channels: List<ChannelEntity>) { values = channels }
        override suspend fun getAllChannels() = values
        override suspend fun getChannel(id: String) = values.firstOrNull { it.id == id }
        override suspend fun clearAll() { values = emptyList() }
        override suspend fun replaceAll(channels: List<ChannelEntity>) {
            writeStarted.complete(Unit)
            gate?.await()
            if (failWrite) throw IOException("Cannot replace rows")
            values = channels
        }
    }
    private class Favorites : FavoriteDao {
        override suspend fun addFavorite(favorite: FavoriteEntity) = Unit
        override suspend fun removeFavorite(channelId: String) = Unit
        override suspend fun getAllFavorites() = emptyList<FavoriteEntity>()
        override fun observeFavoriteIds() = flowOf(emptyList<String>())
        override suspend fun isFavorite(channelId: String) = false
    }
}