package com.yourapp.iptv.di

import android.content.Context
import com.yourapp.iptv.data.datastore.SettingsDataStore
import com.yourapp.iptv.data.local.AppDatabase
import com.yourapp.iptv.domain.repository.SettingsRepository
import com.yourapp.iptv.domain.repository.SubscriptionRepository
import com.yourapp.iptv.domain.repository.ProfileRepository
import com.yourapp.iptv.data.profile.ProfileSettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore =
        SettingsDataStore(context)

    @Provides
    fun provideSettingsRepository(store: SettingsDataStore, profiles: ProfileRepository): SettingsRepository = ProfileSettingsRepository(store,profiles)

    @Provides
    fun provideSubscriptionRepository(store: SettingsDataStore): SubscriptionRepository = store

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)
}