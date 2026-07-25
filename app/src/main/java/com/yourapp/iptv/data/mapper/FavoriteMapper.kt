package com.yourapp.iptv.data.mapper

import com.yourapp.iptv.domain.model.Channel

fun Channel.toFavoriteEntity(): String {
    // في هذه المرحلة سنخزن فقط الـ ID الخاص بالقناة كمفضلة
    return this.id
}