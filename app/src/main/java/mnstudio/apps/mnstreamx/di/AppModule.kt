package mnstudio.apps.mnstreamx.di

import android.content.Context
import mnstudio.apps.mnstreamx.data.datastore.SettingsDataStore
import mnstudio.apps.mnstreamx.data.local.AppDatabase
import mnstudio.apps.mnstreamx.domain.repository.SettingsRepository
import mnstudio.apps.mnstreamx.domain.repository.SubscriptionRepository
import mnstudio.apps.mnstreamx.domain.repository.ProfileRepository
import mnstudio.apps.mnstreamx.data.profile.ProfileSettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore =
        SettingsDataStore(context)

    @Provides
    fun provideSettingsRepository(store: SettingsDataStore, profiles: ProfileRepository): SettingsRepository = ProfileSettingsRepository(store,profiles)

    @Provides
    fun provideSubscriptionRepository(store: SettingsDataStore): SubscriptionRepository = store

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)
}