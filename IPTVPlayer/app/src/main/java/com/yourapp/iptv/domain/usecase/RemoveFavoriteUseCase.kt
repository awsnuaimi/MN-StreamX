package com.yourapp.iptv.domain.usecase

import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.repository.ChannelRepository

class RemoveFavoriteUseCase(
    private val channelRepository: ChannelRepository
) {
    suspend operator fun invoke(channel: Channel) {
        channelRepository.removeFavorite(channel)
    }
}