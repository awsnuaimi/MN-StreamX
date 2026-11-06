package mnstudio.apps.mnstreamx.data.mapper

import mnstudio.apps.mnstreamx.data.local.entity.ChannelEntity
import mnstudio.apps.mnstreamx.data.model.ChannelDto
import mnstudio.apps.mnstreamx.domain.model.Channel

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