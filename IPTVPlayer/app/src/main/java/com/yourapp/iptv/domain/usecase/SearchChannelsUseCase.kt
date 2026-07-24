package com.yourapp.iptv.domain.usecase

import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.repository.ChannelRepository

class SearchChannelsUseCase(
    private val channelRepository: ChannelRepository
) {
    suspend operator fun invoke(query: String): List<Channel> {
        return channelRepository.search(query)
    }
}