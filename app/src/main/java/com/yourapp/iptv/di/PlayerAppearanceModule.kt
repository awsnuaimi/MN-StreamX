package com.yourapp.iptv.di

import android.content.Context
import com.yourapp.iptv.data.datastore.PlayerAppearanceDataStore
import com.yourapp.iptv.data.datastore.playerAppearanceStore
import com.yourapp.iptv.domain.repository.PlayerAppearanceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerAppearanceModule {
    @Provides
    @Singleton
    fun providePlayerAppearance(@ApplicationContext context: Context): PlayerAppearanceRepository =
        PlayerAppearanceDataStore(context.applicationContext.playerAppearanceStore)
}