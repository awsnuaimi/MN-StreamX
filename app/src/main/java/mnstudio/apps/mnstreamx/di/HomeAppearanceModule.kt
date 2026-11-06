package mnstudio.apps.mnstreamx.di

import android.content.Context
import mnstudio.apps.mnstreamx.domain.repository.ProfileRepository
import mnstudio.apps.mnstreamx.data.datastore.HomeAppearanceDataStore
import mnstudio.apps.mnstreamx.data.datastore.homeAppearanceStore
import mnstudio.apps.mnstreamx.domain.repository.HomeAppearanceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HomeAppearanceModule {
    @Provides @Singleton
    fun provideHomeAppearance(@ApplicationContext context: Context, profiles: ProfileRepository): HomeAppearanceRepository =
        HomeAppearanceDataStore(context.applicationContext.homeAppearanceStore, profiles = profiles)
}