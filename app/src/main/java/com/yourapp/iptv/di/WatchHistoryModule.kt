package com.yourapp.iptv.di

import android.content.Context
import com.yourapp.iptv.domain.repository.ProfileRepository
import com.yourapp.iptv.data.datastore.WatchHistoryDataStore
import com.yourapp.iptv.data.datastore.watchHistoryStore
import com.yourapp.iptv.domain.repository.SettingsRepository
import com.yourapp.iptv.domain.repository.WatchHistoryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object WatchHistoryModule {
    @Provides @Singleton
    fun provideHistory(@ApplicationContext context: Context, settings: SettingsRepository, profiles: ProfileRepository): WatchHistoryRepository =
        WatchHistoryDataStore(context.applicationContext.watchHistoryStore, settings,
            CoroutineScope(SupervisorJob() + Dispatchers.IO), profiles = profiles)
}