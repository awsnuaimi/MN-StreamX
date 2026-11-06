package mnstudio.apps.mnstreamx.di

import android.content.Context
import mnstudio.apps.mnstreamx.data.datastore.ChannelSnapshotDataStore
import mnstudio.apps.mnstreamx.data.local.AppDatabase
import mnstudio.apps.mnstreamx.data.repository.ChannelRepositoryImpl
import mnstudio.apps.mnstreamx.data.repository.PlaylistRepositoryImpl
import mnstudio.apps.mnstreamx.data.repository.FileHomeStartupRepository
import mnstudio.apps.mnstreamx.domain.repository.HomeStartupRepository
import mnstudio.apps.mnstreamx.domain.repository.SettingsRepository
import mnstudio.apps.mnstreamx.domain.repository.FavoriteRepository
import mnstudio.apps.mnstreamx.domain.repository.OrganizationRepository
import java.io.File
import mnstudio.apps.mnstreamx.data.source.m3u.M3uDataSource
import mnstudio.apps.mnstreamx.data.source.xtream.XtreamDataSource
import mnstudio.apps.mnstreamx.domain.repository.ChannelRepository
import mnstudio.apps.mnstreamx.domain.repository.PlaylistRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton
import mnstudio.apps.mnstreamx.data.parental.ProtectedChannels
import mnstudio.apps.mnstreamx.data.parental.ProtectedHomeStartup
import mnstudio.apps.mnstreamx.domain.parental.ParentalControl

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    @RawChannelCatalog
    fun provideChannels(database: AppDatabase, @ApplicationContext context: Context, favorites: FavoriteRepository, profiles: mnstudio.apps.mnstreamx.domain.repository.ProfileRepository): ChannelRepository = ChannelRepositoryImpl(
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