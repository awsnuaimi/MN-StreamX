package com.yourapp.iptv.data.source.xtream

import com.yourapp.iptv.data.model.XtreamChannelDto
import com.yourapp.iptv.data.network.HttpClient
import okhttp3.OkHttpClient
import com.yourapp.iptv.util.LoadTimings
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class XtreamDataSource(client: OkHttpClient? = null) {
    private val http by lazy { XtreamHttpClient(client ?: HttpClient.create()) }

    suspend fun fetchChannels(serverUrl: String, username: String, password: String): List<XtreamChannelDto> {
        val urls = XtreamUrls(serverUrl, username, password)
        val extension = LoadTimings.measure(LoadTimings.Stage.XTREAM_ACCOUNT) {
            http.read(urls.api(), 256 * 1024) { reader, _ -> XtreamAccountParser.extension(reader) }
        }
        return LoadTimings.measure(LoadTimings.Stage.XTREAM_LISTS) {
            coroutineScope {
                val categories = async {
                    http.read(urls.api("get_live_categories"), 2 * 1024 * 1024) { reader, active ->
                        XtreamParser.categories(reader, active)
                    }
                }
                val streams = async {
                    http.read(urls.api("get_live_streams"), 20 * 1024 * 1024) { reader, active ->
                        XtreamParser.channels(reader, urls, extension, emptyMap(), active)
                    }
                }
                val names = categories.await()
                streams.await().map { channel ->
                    currentCoroutineContext().ensureActive()
                    channel.copy(categoryName = names[channel.categoryId] ?: channel.categoryName)
                }
            }
        }
    }
}