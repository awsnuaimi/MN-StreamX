package com.yourapp.iptv.di

import android.content.Context
import com.yourapp.iptv.domain.repository.ProfileRepository
import com.yourapp.iptv.data.datastore.SearchHistoryDataStore
import com.yourapp.iptv.data.datastore.searchHistoryStore
import com.yourapp.iptv.data.repository.UnifiedSearchRepositoryImpl
import com.yourapp.iptv.domain.repository.SearchHistoryRepository
import com.yourapp.iptv.domain.repository.UnifiedSearchRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.yourapp.iptv.data.parental.ProtectedSearchHistory
import com.yourapp.iptv.domain.parental.ParentalControl

@Module @InstallIn(SingletonComponent::class)
object SearchModule {
    @Provides @Singleton
    fun provideSearch(impl: UnifiedSearchRepositoryImpl): UnifiedSearchRepository = impl
    @Provides @Singleton
    fun provideSearchHistory(@ApplicationContext context: Context, parental: ParentalControl, profiles: ProfileRepository): SearchHistoryRepository =
        ProtectedSearchHistory(SearchHistoryDataStore(context.applicationContext.searchHistoryStore, profiles = profiles),parental)
}