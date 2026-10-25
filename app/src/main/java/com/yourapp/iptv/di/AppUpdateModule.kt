package com.yourapp.iptv.di

import android.content.Context
import com.yourapp.iptv.BuildConfig
import com.yourapp.iptv.data.update.GitHubAppUpdateRepository
import com.yourapp.iptv.domain.update.AppUpdateRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppUpdateModule {
    @Provides @Singleton
    fun updates(@ApplicationContext context: Context): AppUpdateRepository =
        GitHubAppUpdateRepository(context, BuildConfig.UPDATE_REPOSITORY)
}