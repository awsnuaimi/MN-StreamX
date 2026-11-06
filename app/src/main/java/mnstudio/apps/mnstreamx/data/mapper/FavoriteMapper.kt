package mnstudio.apps.mnstreamx.data.mapper

import mnstudio.apps.mnstreamx.domain.model.Channel

fun Channel.toFavoriteEntity(): String {
    // في هذه المرحلة سنخزن فقط الـ ID الخاص بالقناة كمفضلة
    return this.id
}