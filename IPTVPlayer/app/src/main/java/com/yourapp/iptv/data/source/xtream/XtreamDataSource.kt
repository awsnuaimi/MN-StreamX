package com.yourapp.iptv.data.source.xtream

import com.yourapp.iptv.data.model.XtreamChannelDto
import com.yourapp.iptv.data.network.HttpClient
import okhttp3.Request
import java.io.IOException

class XtreamDataSource {
    private val client = HttpClient.create()

    suspend fun fetchChannels(serverUrl: String, username: String, password: String): List<XtreamChannelDto> {
        // بناء رابط الـ API الخاص بـ Xtream
        val url = "$serverUrl/get_live_streams.php?username=$username&password=$password"
        
        val request = Request.Builder()
            .url(url)
            .build()

        val response = client.newCall(request).execute()
        
        return if (response.isSuccessful) {
            val body = response.body?.string() ?: ""
            XtreamParser.parseChannels(body)
        } else {
            emptyList()
        }
    }
}