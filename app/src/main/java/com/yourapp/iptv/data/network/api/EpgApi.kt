package com.yourapp.iptv.data.network.api

import retrofit2.http.GET
import retrofit2.http.Url

interface EpgApi {
    @GET
    suspend fun getEpgData(@Url url: String): String // سيعود كنص XML
}