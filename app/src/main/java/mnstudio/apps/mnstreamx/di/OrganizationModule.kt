package mnstudio.apps.mnstreamx.di

import android.content.Context
import mnstudio.apps.mnstreamx.data.datastore.OrganizationDataStore
import mnstudio.apps.mnstreamx.data.datastore.organizationStore
import mnstudio.apps.mnstreamx.domain.repository.OrganizationRepository
import mnstudio.apps.mnstreamx.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object OrganizationModule {
    @Provides @Singleton
    fun provideOrganization(@ApplicationContext context: Context, settings: SettingsRepository): OrganizationRepository =
        OrganizationDataStore(context.applicationContext.organizationStore, settings)
}