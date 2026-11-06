package mnstudio.apps.mnstreamx.domain.usecase

import mnstudio.apps.mnstreamx.domain.model.Channel

class FilterChannelsUseCase {
    operator fun invoke(channels: List<Channel>, group: String): List<Channel> {
        return channels.filter { it.group == group }
    }
}