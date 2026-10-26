package com.yourapp.iptv.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Retain the legacy favorites table until its known source is copied in one DAO transaction. */
internal val subscriptionDatabaseMigration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `subscription_favorites` (`sourceKey` TEXT NOT NULL, " +
            "`channelId` TEXT NOT NULL, PRIMARY KEY(`sourceKey`, `channelId`))")
    }
}