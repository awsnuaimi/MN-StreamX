package com.yourapp.iptv.di

import com.yourapp.iptv.data.local.AppDatabase
import com.yourapp.iptv.data.repository.FavoriteRepositoryImpl
import com.yourapp.iptv.domain.repository.FavoriteRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FavoriteModule {
    @Provides
    @Singleton
    fun provideFavorites(database: AppDatabase): FavoriteRepository =
        FavoriteRepositoryImpl(database.favoriteDao())
}