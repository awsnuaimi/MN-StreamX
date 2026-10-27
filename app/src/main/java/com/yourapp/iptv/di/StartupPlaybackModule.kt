package com.yourapp.iptv.di

import android.content.Context
import com.yourapp.iptv.domain.repository.ProfileRepository
import com.yourapp.iptv.data.datastore.StartupPlaybackDataStore
import com.yourapp.iptv.data.datastore.startupPlaybackStore
import com.yourapp.iptv.domain.repository.StartupPlaybackRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object StartupPlaybackModule {
    @Provides @Singleton
    fun provideStartupPlayback(@ApplicationContext context: Context, profiles: ProfileRepository): StartupPlaybackRepository =
        StartupPlaybackDataStore(context.applicationContext.startupPlaybackStore, profiles = profiles)
}