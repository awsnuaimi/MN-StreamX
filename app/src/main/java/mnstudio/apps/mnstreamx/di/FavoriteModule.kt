package mnstudio.apps.mnstreamx.di

import mnstudio.apps.mnstreamx.data.local.AppDatabase
import mnstudio.apps.mnstreamx.data.repository.FavoriteRepositoryImpl
import mnstudio.apps.mnstreamx.domain.repository.FavoriteRepository
import mnstudio.apps.mnstreamx.domain.repository.SubscriptionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FavoriteModule {
    @Provides
    @Singleton
    fun provideFavorites(database: AppDatabase, subscriptions: SubscriptionRepository, profiles: mnstudio.apps.mnstreamx.domain.repository.ProfileRepository): FavoriteRepository =
        FavoriteRepositoryImpl(database.scopedFavoriteDao(), subscriptions,profiles)
}