package mnstudio.apps.mnstreamx.di

import android.content.Context
import mnstudio.apps.mnstreamx.BuildConfig
import mnstudio.apps.mnstreamx.data.update.GitHubAppUpdateRepository
import mnstudio.apps.mnstreamx.domain.update.AppUpdateRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppUpdateModule {
    @Provides @Singleton
    fun updates(@ApplicationContext context: Context): AppUpdateRepository =
        GitHubAppUpdateRepository(context, BuildConfig.UPDATE_REPOSITORY)
}