package mnstudio.apps.mnstreamx.di

import android.content.Context
import mnstudio.apps.mnstreamx.domain.repository.ProfileRepository
import mnstudio.apps.mnstreamx.data.datastore.SearchHistoryDataStore
import mnstudio.apps.mnstreamx.data.datastore.searchHistoryStore
import mnstudio.apps.mnstreamx.data.repository.UnifiedSearchRepositoryImpl
import mnstudio.apps.mnstreamx.domain.repository.SearchHistoryRepository
import mnstudio.apps.mnstreamx.domain.repository.UnifiedSearchRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import mnstudio.apps.mnstreamx.data.parental.ProtectedSearchHistory
import mnstudio.apps.mnstreamx.domain.parental.ParentalControl

@Module @InstallIn(SingletonComponent::class)
object SearchModule {
    @Provides @Singleton
    fun provideSearch(impl: UnifiedSearchRepositoryImpl): UnifiedSearchRepository = impl
    @Provides @Singleton
    fun provideSearchHistory(@ApplicationContext context: Context, parental: ParentalControl, profiles: ProfileRepository): SearchHistoryRepository =
        ProtectedSearchHistory(SearchHistoryDataStore(context.applicationContext.searchHistoryStore, profiles = profiles),parental)
}