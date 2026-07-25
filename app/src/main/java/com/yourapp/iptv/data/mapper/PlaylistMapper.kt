package com.yourapp.iptv.data.mapper

import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.Playlist

fun List<Channel>.toPlaylist(name: String = "My Playlist"): Playlist {
    return Playlist(
        name = name,
        channels = this
    )
}