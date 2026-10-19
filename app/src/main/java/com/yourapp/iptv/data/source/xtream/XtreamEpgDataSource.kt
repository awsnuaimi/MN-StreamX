package com.yourapp.iptv.data.source.xtream

import com.yourapp.iptv.data.network.HttpClient
import com.yourapp.iptv.domain.model.Program
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal class XtreamEpgDataSource(client: OkHttpClient = HttpClient.create()) {
    private val http = XtreamHttpClient(client.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS).build())

    suspend fun fetch(url: HttpUrl): List<Program> = http.read(url, 512 * 1024) { reader, active ->
        XtreamEpgParser.parse(reader, active)
    }
}