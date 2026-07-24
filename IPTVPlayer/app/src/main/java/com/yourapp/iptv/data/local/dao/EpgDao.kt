package com.yourapp.iptv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yourapp.iptv.data.local.entity.ProgramEntity

@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<ProgramEntity>)

    @Query("SELECT * FROM programs WHERE channelId = :channelId")
    suspend fun getProgramsForChannel(channelId: String): List<ProgramEntity>

    @Query("DELETE FROM programs")
    suspend fun clearAll()
}