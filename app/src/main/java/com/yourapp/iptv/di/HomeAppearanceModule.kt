package com.yourapp.iptv.di

import android.content.Context
import com.yourapp.iptv.data.datastore.HomeAppearanceDataStore
import com.yourapp.iptv.data.datastore.homeAppearanceStore
import com.yourapp.iptv.domain.repository.HomeAppearanceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HomeAppearanceModule {
    @Provides @Singleton
    fun provideHomeAppearance(@ApplicationContext context: Context): HomeAppearanceRepository =
        HomeAppearanceDataStore(context.applicationContext.homeAppearanceStore)
}