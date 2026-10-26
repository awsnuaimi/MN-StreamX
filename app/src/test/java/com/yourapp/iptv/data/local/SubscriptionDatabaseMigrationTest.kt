package com.yourapp.iptv.data.local

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SubscriptionDatabaseMigrationTest {
    @Test fun actualVersionOneDatabaseOpensWithAllExistingDataAndTheNewScopedTable() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "synthetic-migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name); path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE channels (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, logoUrl TEXT, streamUrl TEXT NOT NULL, `group` TEXT)")
            old.execSQL("CREATE TABLE favorites (channelId TEXT NOT NULL PRIMARY KEY)")
            old.execSQL("CREATE TABLE programs (id TEXT NOT NULL PRIMARY KEY, channelId TEXT NOT NULL, title TEXT NOT NULL, startTime INTEGER NOT NULL, endTime INTEGER NOT NULL, description TEXT)")
            old.execSQL("INSERT INTO channels VALUES ('shared', 'Synthetic', NULL, 'https://synthetic.invalid/live', 'Test')")
            old.execSQL("INSERT INTO favorites VALUES ('shared')")
            old.execSQL("INSERT INTO programs VALUES ('p', 'shared', 'Synthetic program', 1, 2, NULL)")
            old.version = 1
        }
        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(subscriptionDatabaseMigration).allowMainThreadQueries().build()
        try {
            assertEquals("Synthetic", upgraded.channelDao().getAllChannels().single().name)
            assertEquals("shared", upgraded.favoriteDao().getAllFavorites().single().channelId)
            assertTrue(upgraded.scopedFavoriteDao().observeIds("a".repeat(64)).first().isEmpty())
            upgraded.openHelper.writableDatabase.query("SELECT COUNT(*) FROM programs").use {
                assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
            }
            upgraded.scopedFavoriteDao().migrateLegacy("a".repeat(64))
            assertEquals(listOf("shared"), upgraded.scopedFavoriteDao().observeIds("a".repeat(64)).first())
            assertTrue(upgraded.favoriteDao().getAllFavorites().isEmpty())
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
}