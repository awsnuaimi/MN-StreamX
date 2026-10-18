package com.yourapp.iptv.data.model

data class XtreamChannelDto(
    val streamId: String,
    val name: String,
    val streamUrl: String,
    val icon: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val channelId: String = streamId
) {
    override fun toString(): String = "XtreamChannelDto(streamId=$streamId, streamUrl=redacted)"
}