package com.yourapp.iptv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yourapp.iptv.data.local.dao.ChannelDao
import com.yourapp.iptv.data.local.dao.EpgDao
import com.yourapp.iptv.data.local.dao.FavoriteDao
import com.yourapp.iptv.data.local.dao.ScopedFavoriteDao
import com.yourapp.iptv.data.local.dao.BackupDao
import com.yourapp.iptv.data.local.entity.ChannelEntity
import com.yourapp.iptv.data.local.entity.FavoriteEntity
import com.yourapp.iptv.data.local.entity.ScopedFavoriteEntity
import com.yourapp.iptv.data.local.entity.ProgramEntity

@Database(
    entities = [ChannelEntity::class, FavoriteEntity::class, ProgramEntity::class, ScopedFavoriteEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun scopedFavoriteDao(): ScopedFavoriteDao
    abstract fun profileFavoriteDao(): com.yourapp.iptv.data.local.dao.ProfileFavoriteDao
    abstract fun backupDao(): BackupDao
    abstract fun epgDao(): EpgDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "iptv_database"
                ).addMigrations(subscriptionDatabaseMigration).build().also { INSTANCE = it }
            }
        }
    }
}