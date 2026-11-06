package mnstudio.apps.mnstreamx.di

import android.content.Context
import mnstudio.apps.mnstreamx.domain.repository.ProfileRepository
import mnstudio.apps.mnstreamx.data.datastore.PlayerAppearanceDataStore
import mnstudio.apps.mnstreamx.data.datastore.playerAppearanceStore
import mnstudio.apps.mnstreamx.domain.repository.PlayerAppearanceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerAppearanceModule {
    @Provides
    @Singleton
    fun providePlayerAppearance(@ApplicationContext context: Context, profiles: ProfileRepository): PlayerAppearanceRepository =
        PlayerAppearanceDataStore(context.applicationContext.playerAppearanceStore, profiles = profiles)
}