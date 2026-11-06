package mnstudio.apps.mnstreamx.domain.usecase

import mnstudio.apps.mnstreamx.domain.model.Channel

class SortChannelsUseCase {
    operator fun invoke(channels: List<Channel>): List<Channel> {
        return channels.sortedBy { it.name }
    }
}