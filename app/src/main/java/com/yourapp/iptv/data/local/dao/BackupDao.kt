package com.yourapp.iptv.data.local.dao

import androidx.room.*
import com.yourapp.iptv.data.local.entity.ScopedFavoriteEntity
import com.yourapp.iptv.data.local.entity.FavoriteEntity

@Dao
interface BackupDao {
    @Query("SELECT * FROM subscription_favorites ORDER BY sourceKey, channelId")
    suspend fun scoped(): List<ScopedFavoriteEntity>
    @Query("SELECT channelId FROM favorites ORDER BY channelId")
    suspend fun legacy(): List<String>
    @Query("DELETE FROM subscription_favorites")
    suspend fun clearScoped()
    @Query("DELETE FROM favorites")
    suspend fun clearLegacy()
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScoped(values: List<ScopedFavoriteEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLegacy(values: List<FavoriteEntity>)
    @Transaction
    suspend fun replace(scoped: List<ScopedFavoriteEntity>, legacy: List<String>) {
        clearScoped(); clearLegacy()
        insertScoped(scoped); insertLegacy(legacy.map(::FavoriteEntity))
    }
}