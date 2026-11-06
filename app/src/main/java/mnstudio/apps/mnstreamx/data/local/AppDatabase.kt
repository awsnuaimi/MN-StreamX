package mnstudio.apps.mnstreamx.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import mnstudio.apps.mnstreamx.data.local.dao.ChannelDao
import mnstudio.apps.mnstreamx.data.local.dao.EpgDao
import mnstudio.apps.mnstreamx.data.local.dao.FavoriteDao
import mnstudio.apps.mnstreamx.data.local.dao.ScopedFavoriteDao
import mnstudio.apps.mnstreamx.data.local.dao.BackupDao
import mnstudio.apps.mnstreamx.data.local.entity.ChannelEntity
import mnstudio.apps.mnstreamx.data.local.entity.FavoriteEntity
import mnstudio.apps.mnstreamx.data.local.entity.ScopedFavoriteEntity
import mnstudio.apps.mnstreamx.data.local.entity.ProgramEntity

@Database(
    entities = [ChannelEntity::class, FavoriteEntity::class, ProgramEntity::class, ScopedFavoriteEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun scopedFavoriteDao(): ScopedFavoriteDao
    abstract fun profileFavoriteDao(): mnstudio.apps.mnstreamx.data.local.dao.ProfileFavoriteDao
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