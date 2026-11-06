package mnstudio.apps.mnstreamx.domain.usecase

import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.repository.ChannelRepository

class SearchChannelsUseCase(
    private val channelRepository: ChannelRepository
) {
    suspend operator fun invoke(query: String): List<Channel> {
        return channelRepository.search(query)
    }
}