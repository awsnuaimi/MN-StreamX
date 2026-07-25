package com.yourapp.iptv.domain.usecase

import com.yourapp.iptv.domain.model.Channel

class FilterChannelsUseCase {
    operator fun invoke(channels: List<Channel>, group: String): List<Channel> {
        return channels.filter { it.group == group }
    }
}