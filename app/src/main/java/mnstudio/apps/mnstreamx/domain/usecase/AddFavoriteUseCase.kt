package mnstudio.apps.mnstreamx.domain.usecase

import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.repository.ChannelRepository

class AddFavoriteUseCase(
    private val channelRepository: ChannelRepository
) {
    suspend operator fun invoke(channel: Channel) {
        channelRepository.addFavorite(channel)
    }
}