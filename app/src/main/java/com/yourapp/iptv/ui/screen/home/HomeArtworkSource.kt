package com.yourapp.iptv.ui.screen.home

import com.yourapp.iptv.domain.model.Channel

/** Channel artwork is a logo, never a cropped program poster or live frame. */
internal sealed interface HomeArtworkSource {
    data class Logo(val url: String) : HomeArtworkSource
    data class Monogram(val text: String) : HomeArtworkSource
    data object Empty : HomeArtworkSource

    companion object {
        fun of(channel: Channel): HomeArtworkSource =
            HomeImageSource.remote(channel.logoUrl)?.let(::Logo) ?: fallback(channel.name)

        /** Also used when a syntactically valid logo URL fails to load. */
        fun fallback(name: String): HomeArtworkSource = if (name.isNotBlank()) {
            Monogram(ChannelMonogram.of(name))
        } else Empty
    }
}