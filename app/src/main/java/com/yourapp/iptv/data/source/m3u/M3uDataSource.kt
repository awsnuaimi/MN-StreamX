package com.yourapp.iptv.data.source.m3u

import com.yourapp.iptv.data.network.HttpClient
import com.yourapp.iptv.data.model.ChannelDto
import okhttp3.Request
import java.io.IOException

class M3uDataSource {
    private val client = HttpClient.create()

    suspend fun fetchChannels(url: String): List<ChannelDto> {
        val request = Request.Builder()
            .url(url)
            .build()

        val response = client.newCall(request).execute()
        
        return if (response.isSuccessful) {
            val body = response.body?.string() ?: ""
            M3uParser.parse(body)
        } else {
            emptyList()
        }
    }
}