package com.yourapp.iptv.data.model

data class XtreamChannelDto(
    val streamId: String,
    val name: String,
    val streamUrl: String,
    val icon: String? = null,
    val categoryId: String? = null
)