package com.yourapp.iptv.data.parental

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.*
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException

internal class PinMemory : ParentalStorage {
    var value: ParentalRecord? = null
    var failRead = false; var failWrite = false; var writes = 0
    var beforeWrite: () -> Unit = {}
    override fun read(): ParentalRecord? { if (failRead) throw IOException("Synthetic read failure"); return value }
    override fun write(record: ParentalRecord?) {
        beforeWrite(); if (failWrite) throw IOException("Synthetic write failure")
        writes++; value = record
    }
}
internal class PinClock : ParentalClock {
    var now = 1000L; var bootId = 1
    override fun elapsed() = now
    override fun boot() = bootId
}
internal class Access(initial: ParentalState = ParentalState(ready = true)) : ParentalControl by ParentalControl.Disabled {
    override val state = MutableStateFlow(initial)
    override fun lock() { state.value = state.value.copy(unlocked = false,epoch = state.value.epoch + 1) }
    override fun background() = lock()
}
internal class ParentalChannels : ChannelRepository {
    val safe = Channel("1","Public channel",streamUrl = "https://synthetic.invalid/1",group = "Family",isFavorite = true,folderIds = listOf("family"))
    val hidden = Channel("2","Protected channel",streamUrl = "https://synthetic.invalid/2",group = "Protected",isFavorite = true,folderIds = listOf("protected"))
    var rows = listOf(safe,hidden)
    var provenance: String? = null
    var imports = 0
    var beforeRead: suspend () -> Unit = {}
    override suspend fun getCachedChannels(source: AppSettings): List<Channel>? {
        beforeRead(); return rows.takeIf { provenance == null || provenance == PlaylistCacheKey.of(source) }
    }
    override suspend fun getPlaybackChannels(source: AppSettings, ids: Set<String>) = getCachedChannels(source).orEmpty().filter { it.id in ids }
    override suspend fun getPlaybackChannel(id: String) = rows.firstOrNull { it.id == id }
    override suspend fun getChannel(id: String) = getPlaybackChannel(id)
    override suspend fun getChannels() = rows
    override suspend fun loadFromM3u(url: String) = rows.also { imports++ }
    override suspend fun loadFromXtream(serverUrl: String, username: String, password: String) = rows.also { imports++ }
    override suspend fun search(query: String) = rows.filter { it.name.contains(query) }
    override suspend fun getFavorites() = rows.filter { it.isFavorite }
    override suspend fun addFavorite(channel: Channel) = Unit
    override suspend fun removeFavorite(channel: Channel) = Unit
}
internal class ParentalVod : VodRepository {
    var requests = 0
    var beforeReturn: suspend () -> Unit = {}
    val item = VodItem("1","Protected movie","10")
    override fun supports(settings: AppSettings) = true
    override suspend fun catalog(settings: AppSettings, kind: VodKind): VodCatalog {
        requests++; beforeReturn(); return VodCatalog(listOf(VodCategory("10","Category")),listOf(item))
    }
    override suspend fun category(settings: AppSettings, kind: VodKind, categoryId: String) = listOf(item).also { requests++ }
    override suspend fun details(settings: AppSettings, kind: VodKind, item: VodItem) = VodDetails(item).also { requests++ }
    override fun playbackUrl(settings: AppSettings, kind: VodKind, id: String, extension: String) = "https://synthetic.invalid/$id.$extension".also { requests++ }
}