package com.yourapp.iptv.domain.usecase

import com.yourapp.iptv.domain.model.Channel

class PlayChannelUseCase {
    operator fun invoke(channel: Channel): String {
        // هنا فقط نعيد رابط البث، التشغيل الفعلي سيكون في الـ Player
        return channel.streamUrl
    }
}