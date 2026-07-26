package com.yourapp.iptv.data.mapper

import com.yourapp.iptv.data.local.entity.ChannelEntity
import com.yourapp.iptv.data.model.ChannelDto
import com.yourapp.iptv.domain.model.Channel

fun ChannelDto.toDomain(): Channel {
    return Channel(
        id = this.id,
        name = this.name,
        logoUrl = this.logo,
        streamUrl = this.url,
        group = this.group,
        isFavorite = false
    )
}

fun ChannelEntity.toDomain(): Channel {
    return Channel(
        id = this.id,
        name = this.name,
        logoUrl = this.logoUrl,
        streamUrl = this.streamUrl,
        group = this.group,
        isFavorite = false
    )
}