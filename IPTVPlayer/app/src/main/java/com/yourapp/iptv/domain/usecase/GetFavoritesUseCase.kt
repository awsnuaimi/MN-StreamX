package com.yourapp.iptv.domain.usecase

import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.repository.ChannelRepository

class GetFavoritesUseCase(
    private val channelRepository: ChannelRepository
) {
    suspend operator fun invoke(): List<Channel> {
        return channelRepository.getFavorites()
    }
}