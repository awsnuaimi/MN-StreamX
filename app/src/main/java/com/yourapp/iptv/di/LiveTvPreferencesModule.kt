package com.yourapp.iptv.di

import android.content.Context
import com.yourapp.iptv.data.datastore.LiveTvPreferencesDataStore
import com.yourapp.iptv.data.datastore.SettingsDataStore
import com.yourapp.iptv.data.datastore.organizationStore
import com.yourapp.iptv.domain.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object LiveTvPreferencesModule {
    @Provides @Singleton
    fun preferences(@ApplicationContext context: Context, settings: SettingsDataStore,
        profiles: ProfileRepository): LiveTvPreferencesRepository =
        LiveTvPreferencesDataStore(context.applicationContext.organizationStore, settings::committedSettings, profiles)
}