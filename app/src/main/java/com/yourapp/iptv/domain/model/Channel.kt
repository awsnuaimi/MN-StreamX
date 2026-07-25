package com.yourapp.iptv.domain.model

data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String? = null,
    val streamUrl: String,
    val group: String? = null,
    val isFavorite: Boolean = false
)