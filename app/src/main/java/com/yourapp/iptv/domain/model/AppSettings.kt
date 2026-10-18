package com.yourapp.iptv.domain.model

data class AppSettings(
    val m3uUrl: String = "",
    val xtreamServer: String = "",
    val xtreamUsername: String = "",
    val xtreamPassword: String = "",
    val epgUrl: String = ""
)