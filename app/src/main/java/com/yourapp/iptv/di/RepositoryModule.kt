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
import com.yourapp.iptv.data.parental.ProtectedChannels
import com.yourapp.iptv.data.parental.ProtectedHomeStartup
import com.yourapp.iptv.domain.parental.ParentalControl

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    @RawChannelCatalog
    fun provideChannels(database: AppDatabase, @ApplicationContext context: Context, favorites: FavoriteRepository, profiles: com.yourapp.iptv.domain.repository.ProfileRepository): ChannelRepository = ChannelRepositoryImpl(
        m3uDataSource = M3uDataSource(),
        xtreamDataSource = XtreamDataSource(),
        channelDao = database.channelDao(),
        favoriteDao = database.favoriteDao(),
        snapshot = ChannelSnapshotDataStore(context), favorites = favorites, profiles = profiles
    )

    @Provides @Singleton
    fun protectedChannels(@RawChannelCatalog raw: ChannelRepository, settings: SettingsRepository,
        parental: ParentalControl): ChannelRepository = ProtectedChannels(raw,settings,parental)

    @Provides
    @Singleton
    fun providePlaylists(channels: ChannelRepository): PlaylistRepository =
        PlaylistRepositoryImpl(channels)

    @Provides @Singleton
    fun provideHomeStartup(@ApplicationContext context: Context, settings: SettingsRepository,
        organization: OrganizationRepository, parental: ParentalControl,
        @RawChannelCatalog complete: ChannelRepository): HomeStartupRepository = ProtectedHomeStartup(
        FileHomeStartupRepository(File(context.noBackupFilesDir, "home_catalog_v24.bin"),settings,organization),parental,complete)
}