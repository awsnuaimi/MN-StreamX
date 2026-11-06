package mnstudio.apps.mnstreamx.data.local.entity

import androidx.room.Entity

@Entity(tableName = "subscription_favorites", primaryKeys = ["sourceKey", "channelId"])
data class ScopedFavoriteEntity(val sourceKey: String, val channelId: String)