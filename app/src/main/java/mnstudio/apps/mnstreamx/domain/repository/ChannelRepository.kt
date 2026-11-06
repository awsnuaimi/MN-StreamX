package mnstudio.apps.mnstreamx.domain.repository

import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.model.AppSettings

interface ChannelRepository {
    suspend fun getPlaybackChannel(id: String): Channel? = null
    suspend fun getPlaybackChannel(source: AppSettings, id: String): Channel? =
        getPlaybackChannels(source, setOf(id)).firstOrNull()
    suspend fun getCachedChannels(source: AppSettings): List<Channel>? = null
    suspend fun getPlaybackChannels(source: AppSettings, ids: Set<String>): List<Channel> =
        getCachedChannels(source).orEmpty().filter { it.id in ids }
    suspend fun getChannels(): List<Channel>
    suspend fun getChannel(id: String): Channel?
    suspend fun loadFromM3u(url: String): List<Channel>
    suspend fun loadFromXtream(serverUrl: String, username: String, password: String): List<Channel>
    suspend fun search(query: String): List<Channel>
    suspend fun getFavorites(): List<Channel>
    suspend fun addFavorite(channel: Channel)
    suspend fun removeFavorite(channel: Channel)
}