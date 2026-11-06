package mnstudio.apps.mnstreamx.domain.usecase

import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.repository.ChannelRepository

class GetFavoritesUseCase(
    private val channelRepository: ChannelRepository
) {
    suspend operator fun invoke(): List<Channel> {
        return channelRepository.getFavorites()
    }
}