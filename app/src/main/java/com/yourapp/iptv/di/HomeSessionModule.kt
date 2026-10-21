package com.yourapp.iptv.di

import android.content.Context
import com.yourapp.iptv.data.datastore.HomeSessionDataStore
import com.yourapp.iptv.data.datastore.homeSessionStore
import com.yourapp.iptv.domain.repository.HomeSessionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HomeSessionModule {
    @Provides @Singleton
    fun provideHomeSession(@ApplicationContext context: Context): HomeSessionRepository =
        HomeSessionDataStore(context.applicationContext.homeSessionStore,
            CoroutineScope(SupervisorJob() + Dispatchers.IO))
}