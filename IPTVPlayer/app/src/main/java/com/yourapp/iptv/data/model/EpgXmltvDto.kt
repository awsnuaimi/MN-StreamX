package com.yourapp.iptv.data.model

data class EpgXmltvDto(
    val channelId: String,
    val title: String,
    val start: String, // صيغة الوقت ستكون نصية في البداية
    val stop: String,
    val desc: String? = null
)