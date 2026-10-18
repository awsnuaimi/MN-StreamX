package com.yourapp.iptv.data.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object HttpClient {
    private val shared: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    fun create(): OkHttpClient = shared
}
