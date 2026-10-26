package com.yourapp.iptv.di

import android.content.Context
import com.yourapp.iptv.data.backup.*
import com.yourapp.iptv.data.local.AppDatabase
import com.yourapp.iptv.domain.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object BackupModule {
    @Provides @Singleton
    fun provideBackup(@ApplicationContext context: Context, database: AppDatabase, subscriptions: SubscriptionRepository): BackupRepository =
        EncryptedBackupRepository(BackupStorage(context, database, subscriptions),
            DeviceBackupJournal(File(context.noBackupFilesDir, "backup_restore_v1.journal")))
    @Provides @Singleton
    fun provideBackupDocuments(@ApplicationContext context: Context): BackupDocuments = AndroidBackupDocuments(context.contentResolver)
}