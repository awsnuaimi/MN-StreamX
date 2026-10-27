package com.yourapp.iptv.data.local.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface ProfileFavoriteDao {
    @Query("DELETE FROM subscription_favorites WHERE substr(sourceKey, 1, length(:prefix)) = :prefix")
    suspend fun removeNamespace(prefix: String)
}