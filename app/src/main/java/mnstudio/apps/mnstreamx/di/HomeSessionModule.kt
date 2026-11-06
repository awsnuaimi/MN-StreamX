package mnstudio.apps.mnstreamx.di

import android.content.Context
import mnstudio.apps.mnstreamx.domain.repository.ProfileRepository
import mnstudio.apps.mnstreamx.data.datastore.HomeSessionDataStore
import mnstudio.apps.mnstreamx.data.datastore.homeSessionStore
import mnstudio.apps.mnstreamx.domain.repository.HomeSessionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HomeSessionModule {
    @Provides @Singleton
    fun provideHomeSession(@ApplicationContext context: Context, profiles: ProfileRepository): HomeSessionRepository =
        HomeSessionDataStore(context.applicationContext.homeSessionStore,
            CoroutineScope(SupervisorJob() + Dispatchers.IO), profiles = profiles)
}