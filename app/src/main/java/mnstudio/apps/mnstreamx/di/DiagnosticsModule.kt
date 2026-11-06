package mnstudio.apps.mnstreamx.di

import android.content.Context
import mnstudio.apps.mnstreamx.data.diagnostics.AndroidDiagnosticsRepository
import mnstudio.apps.mnstreamx.domain.repository.DiagnosticsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object DiagnosticsModule {
    @Provides @Singleton
    fun provideDiagnostics(@ApplicationContext context: Context): DiagnosticsRepository = AndroidDiagnosticsRepository(context)
}