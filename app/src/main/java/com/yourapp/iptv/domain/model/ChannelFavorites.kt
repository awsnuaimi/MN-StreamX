package com.yourapp.iptv.domain.model

object ChannelFavorites {
    fun apply(channels: List<Channel>, ids: Set<String>?): List<Channel> {
        if (ids == null) return channels
        var changed: MutableList<Channel>? = null
        channels.forEachIndexed { index, channel ->
            val favorite = channel.id in ids
            if (channel.isFavorite != favorite) {
                val result = changed ?: channels.toMutableList().also { changed = it }
                result[index] = channel.copy(isFavorite = favorite)
            }
        }
        return changed ?: channels
    }
}