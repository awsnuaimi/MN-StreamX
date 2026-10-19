package com.yourapp.iptv.domain.model

object ChannelFavorites {
    fun apply(channels: List<Channel>, ids: Set<String>?): List<Channel> {
        if (ids == null) return channels
        return channels.map { channel ->
            val favorite = channel.id in ids
            if (channel.isFavorite == favorite) channel else channel.copy(isFavorite = favorite)
        }
    }
}