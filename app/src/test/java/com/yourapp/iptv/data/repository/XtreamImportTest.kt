package com.yourapp.iptv.data.repository

import com.yourapp.iptv.data.local.dao.ChannelDao
import com.yourapp.iptv.data.local.dao.FavoriteDao
import com.yourapp.iptv.data.local.entity.ChannelEntity
import com.yourapp.iptv.data.local.entity.FavoriteEntity
import com.yourapp.iptv.data.source.m3u.M3uDataSource
import com.yourapp.iptv.data.source.xtream.XtreamDataSource
import com.yourapp.iptv.domain.model.PlaylistLoadException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class XtreamImportTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private val database = Channels()
    private val favorites = Favorites()

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
    }

    @After fun tearDown() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        server.shutdown()
    }

    @Test fun failureKeepsStoredChannelsAndSuccessPreservesFavoriteAcrossRefresh() = runBlocking {
        val previous = database.values.toList()
        val repository = ChannelRepositoryImpl(M3uDataSource(client), XtreamDataSource(client), database, favorites)
        server.enqueue(MockResponse().setBody("""{"user_info":{"auth":0}}"""))
        assertTrue(runCatching { repository.loadFromXtream(server.url("/").toString(), "user", "password") }
            .exceptionOrNull() is PlaylistLoadException)
        assertEquals(previous, database.values)
        enqueueSuccess()
        val first = repository.loadFromXtream(server.url("/").toString(), "user", "password").single()
        repository.addFavorite(first)
        enqueueSuccess()
        val refreshed = repository.loadFromXtream(server.url("/").toString(), "user", "new-password").single()
        assertEquals(first.id, refreshed.id)
        assertTrue(refreshed.isFavorite)
        assertEquals("News", refreshed.group)
        assertTrue(refreshed.streamUrl.contains("new-password"))
    }

    private fun enqueueSuccess() {
        server.enqueue(MockResponse().setBody("""{"user_info":{"auth":1,"status":"Active"}}"""))
        server.enqueue(MockResponse().setBody("""[{"category_id":1,"category_name":"News"}]"""))
        server.enqueue(MockResponse().setBody("""[{"stream_id":12,"name":"Test","category_id":"1"}]"""))
    }

    private class Channels : ChannelDao {
        var values = listOf(ChannelEntity("previous", "Previous", null, "https://unit.test/old.ts"))
        override suspend fun insertChannels(channels: List<ChannelEntity>) { values = channels }
        override suspend fun getAllChannels() = values
        override suspend fun getChannel(id: String) = values.firstOrNull { it.id == id }
        override suspend fun clearAll() { values = emptyList() }
    }

    private class Favorites : FavoriteDao {
        private val values = mutableMapOf<String, FavoriteEntity>()
        override suspend fun addFavorite(favorite: FavoriteEntity) { values[favorite.channelId] = favorite }
        override suspend fun removeFavorite(channelId: String) { values.remove(channelId) }
        override suspend fun getAllFavorites() = values.values.toList()
    }
}