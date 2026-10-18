package com.yourapp.iptv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.yourapp.iptv.data.local.entity.ChannelEntity

@Dao
interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("SELECT * FROM channels")
    suspend fun getAllChannels(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun getChannel(id: String): ChannelEntity?

    @Transaction
    suspend fun replaceAll(channels: List<ChannelEntity>) {
        clearAll()
        insertChannels(channels)
    }

    @Query("DELETE FROM channels")
    suspend fun clearAll()
}
