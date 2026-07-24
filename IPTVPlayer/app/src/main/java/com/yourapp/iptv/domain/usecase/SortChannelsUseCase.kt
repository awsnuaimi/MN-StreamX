package com.yourapp.iptv.domain.usecase

import com.yourapp.iptv.domain.model.Channel

class SortChannelsUseCase {
    operator fun invoke(channels: List<Channel>): List<Channel> {
        return channels.sortedBy { it.name }
    }
}