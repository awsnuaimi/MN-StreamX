package com.yourapp.iptv.data.local.dao

import androidx.room.*
import com.yourapp.iptv.data.local.entity.ScopedFavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScopedFavoriteDao {
    @Query("SELECT channelId FROM subscription_favorites WHERE sourceKey = :sourceKey")
    fun observeIds(sourceKey: String): Flow<List<String>>
    @Query("SELECT EXISTS(SELECT 1 FROM subscription_favorites WHERE sourceKey = :sourceKey AND channelId = :channelId)")
    suspend fun contains(sourceKey: String, channelId: String): Boolean
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(value: ScopedFavoriteEntity)
    @Query("DELETE FROM subscription_favorites WHERE sourceKey = :sourceKey AND channelId = :channelId")
    suspend fun remove(sourceKey: String, channelId: String)
    @Query("INSERT OR IGNORE INTO subscription_favorites (sourceKey, channelId) SELECT :sourceKey, channelId FROM favorites")
    suspend fun copyLegacy(sourceKey: String)
    @Query("DELETE FROM favorites")
    suspend fun clearLegacy()
    @Transaction
    suspend fun migrateLegacy(sourceKey: String) { copyLegacy(sourceKey); clearLegacy() }
    @Transaction
    suspend fun toggle(sourceKey: String, channelId: String) {
        if (contains(sourceKey, channelId)) remove(sourceKey, channelId)
        else add(ScopedFavoriteEntity(sourceKey, channelId))
    }
}