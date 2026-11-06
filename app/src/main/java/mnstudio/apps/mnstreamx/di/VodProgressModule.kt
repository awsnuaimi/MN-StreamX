package mnstudio.apps.mnstreamx.di

import android.content.Context
import mnstudio.apps.mnstreamx.domain.repository.ProfileRepository
import mnstudio.apps.mnstreamx.data.datastore.VodProgressDataStore
import mnstudio.apps.mnstreamx.data.datastore.vodProgressStore
import mnstudio.apps.mnstreamx.domain.repository.SettingsRepository
import mnstudio.apps.mnstreamx.domain.repository.VodProgressRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton
import mnstudio.apps.mnstreamx.data.parental.ProtectedProgress
import mnstudio.apps.mnstreamx.domain.parental.ParentalControl

@Module
@InstallIn(SingletonComponent::class)
object VodProgressModule {
    @Provides @Singleton
    fun provideProgress(@ApplicationContext context: Context, settings: SettingsRepository,
        parental: ParentalControl, profiles: ProfileRepository): VodProgressRepository =
        ProtectedProgress(VodProgressDataStore(context.applicationContext.vodProgressStore, settings,
            CoroutineScope(SupervisorJob() + Dispatchers.IO), profiles = profiles),parental)
}