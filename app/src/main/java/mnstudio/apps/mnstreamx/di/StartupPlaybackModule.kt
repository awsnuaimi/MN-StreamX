package mnstudio.apps.mnstreamx.di

import android.content.Context
import mnstudio.apps.mnstreamx.domain.repository.ProfileRepository
import mnstudio.apps.mnstreamx.data.datastore.StartupPlaybackDataStore
import mnstudio.apps.mnstreamx.data.datastore.startupPlaybackStore
import mnstudio.apps.mnstreamx.domain.repository.StartupPlaybackRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object StartupPlaybackModule {
    @Provides @Singleton
    fun provideStartupPlayback(@ApplicationContext context: Context, profiles: ProfileRepository): StartupPlaybackRepository =
        StartupPlaybackDataStore(context.applicationContext.startupPlaybackStore, profiles = profiles)
}