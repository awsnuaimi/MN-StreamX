package mnstudio.apps.mnstreamx.di

import android.content.Context
import mnstudio.apps.mnstreamx.data.datastore.LiveTvPreferencesDataStore
import mnstudio.apps.mnstreamx.data.datastore.SettingsDataStore
import mnstudio.apps.mnstreamx.data.datastore.organizationStore
import mnstudio.apps.mnstreamx.domain.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object LiveTvPreferencesModule {
    @Provides @Singleton
    fun preferences(@ApplicationContext context: Context, settings: SettingsDataStore,
        profiles: ProfileRepository): LiveTvPreferencesRepository =
        LiveTvPreferencesDataStore(context.applicationContext.organizationStore, settings::committedSettings, profiles)
}