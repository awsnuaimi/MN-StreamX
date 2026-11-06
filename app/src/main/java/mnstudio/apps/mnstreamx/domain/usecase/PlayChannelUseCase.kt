package mnstudio.apps.mnstreamx.domain.usecase

import mnstudio.apps.mnstreamx.domain.model.Channel

class PlayChannelUseCase {
    operator fun invoke(channel: Channel): String {
        // هنا فقط نعيد رابط البث، التشغيل الفعلي سيكون في الـ Player
        return channel.streamUrl
    }
}