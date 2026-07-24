package com.yourapp.iptv.domain.model

data class Playlist(
    val name: String,
    val channels: List<Channel>
)