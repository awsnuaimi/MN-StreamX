package mnstudio.apps.mnstreamx.di

import android.content.Context
import mnstudio.apps.mnstreamx.domain.repository.ProfileRepository
import mnstudio.apps.mnstreamx.data.datastore.WatchHistoryDataStore
import mnstudio.apps.mnstreamx.data.datastore.watchHistoryStore
import mnstudio.apps.mnstreamx.domain.repository.SettingsRepository
import mnstudio.apps.mnstreamx.domain.repository.WatchHistoryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object WatchHistoryModule {
    @Provides @Singleton
    fun provideHistory(@ApplicationContext context: Context, settings: SettingsRepository, profiles: ProfileRepository): WatchHistoryRepository =
        WatchHistoryDataStore(context.applicationContext.watchHistoryStore, settings,
            CoroutineScope(SupervisorJob() + Dispatchers.IO), profiles = profiles)
}