package com.yourapp.iptv.di

import android.content.Context
import com.yourapp.iptv.data.datastore.ChannelSnapshotDataStore
import com.yourapp.iptv.data.local.AppDatabase
import com.yourapp.iptv.data.repository.ChannelRepositoryImpl
import com.yourapp.iptv.data.repository.PlaylistRepositoryImpl
import com.yourapp.iptv.data.repository.FileHomeStartupRepository
import com.yourapp.iptv.domain.repository.HomeStartupRepository
import com.yourapp.iptv.domain.repository.SettingsRepository
import com.yourapp.iptv.domain.repository.FavoriteRepository
import com.yourapp.iptv.domain.repository.OrganizationRepository
import java.io.File
import com.yourapp.iptv.data.source.m3u.M3uDataSource
import com.yourapp.iptv.data.source.xtream.XtreamDataSource
import com.yourapp.iptv.domain.repository.ChannelRepository
import com.yourapp.iptv.domain.repository.PlaylistRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideChannels(database: AppDatabase, @ApplicationContext context: Context, favorites: FavoriteRepository): ChannelRepository = ChannelRepositoryImpl(
        m3uDataSource = M3uDataSource(),
        xtreamDataSource = XtreamDataSource(),
        channelDao = database.channelDao(),
        favoriteDao = database.favoriteDao(),
        snapshot = ChannelSnapshotDataStore(context), favorites = favorites
    )

    @Provides
    @Singleton
    fun providePlaylists(channels: ChannelRepository): PlaylistRepository =
        PlaylistRepositoryImpl(channels)

    @Provides @Singleton
    fun provideHomeStartup(@ApplicationContext context: Context, settings: SettingsRepository,
        organization: OrganizationRepository): HomeStartupRepository = FileHomeStartupRepository(
        File(context.noBackupFilesDir, "home_catalog_v24.bin"), settings, organization)
}