package mnstudio.apps.mnstreamx.di

import android.content.Context
import mnstudio.apps.mnstreamx.data.repository.XtreamEpgRepository
import mnstudio.apps.mnstreamx.data.source.epg.XtreamEpgCache
import mnstudio.apps.mnstreamx.data.source.xtream.XtreamEpgDataSource
import mnstudio.apps.mnstreamx.domain.repository.LiveEpgRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EpgModule {
    @Provides
    @Singleton
    fun provideLiveEpg(@ApplicationContext context: Context): LiveEpgRepository = XtreamEpgRepository(
        cache = XtreamEpgCache(File(context.cacheDir, "xtream-epg-v1")),
        fetch = XtreamEpgDataSource()::fetch
    )
}