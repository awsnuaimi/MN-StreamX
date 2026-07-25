package com.yourapp.iptv.data.source.epg

import com.yourapp.iptv.data.model.EpgXmltvDto
import com.yourapp.iptv.data.network.HttpClient
import okhttp3.Request

class EpgDataSource {
    private val client = HttpClient.create()
    private val parser = EpgParser()

    suspend fun fetchEpg(url: String): List<EpgXmltvDto> {
        val request = Request.Builder()
            .url(url)
            .build()

        val response = client.newCall(request).execute()
        
        return if (response.isSuccessful) {
            val body = response.body?.string() ?: ""
            parser.parse(body)
        } else {
            emptyList()
        }
    }
}