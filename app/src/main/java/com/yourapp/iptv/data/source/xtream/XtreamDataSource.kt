package com.yourapp.iptv.data.source.xtream

import com.yourapp.iptv.data.model.XtreamChannelDto
import com.yourapp.iptv.data.network.HttpClient
import okhttp3.OkHttpClient

class XtreamDataSource(client: OkHttpClient = HttpClient.create()) {
    private val http = XtreamHttpClient(client)

    suspend fun fetchChannels(serverUrl: String, username: String, password: String): List<XtreamChannelDto> {
        val urls = XtreamUrls(serverUrl, username, password)
        val extension = http.read(urls.api(), 256 * 1024) { reader, _ ->
            XtreamAccountParser.extension(reader)
        }
        val categories = http.read(urls.api("get_live_categories"), 2 * 1024 * 1024) { reader, active ->
            XtreamParser.categories(reader, active)
        }
        return http.read(urls.api("get_live_streams"), 20 * 1024 * 1024) { reader, active ->
            XtreamParser.channels(reader, urls, extension, categories, active)
        }
    }
}