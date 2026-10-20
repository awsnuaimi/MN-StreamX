package com.yourapp.iptv.di

import android.content.Context
import com.yourapp.iptv.data.datastore.ChannelSnapshotDataStore
import com.yourapp.iptv.data.local.AppDatabase
import com.yourapp.iptv.data.repository.ChannelRepositoryImpl
import com.yourapp.iptv.data.repository.PlaylistRepositoryImpl
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
    fun provideChannels(database: AppDatabase, @ApplicationContext context: Context): ChannelRepository = ChannelRepositoryImpl(
        m3uDataSource = M3uDataSource(),
        xtreamDataSource = XtreamDataSource(),
        channelDao = database.channelDao(),
        favoriteDao = database.favoriteDao(),
        snapshot = ChannelSnapshotDataStore(context)
    )

    @Provides
    @Singleton
    fun providePlaylists(channels: ChannelRepository): PlaylistRepository =
        PlaylistRepositoryImpl(channels)
}