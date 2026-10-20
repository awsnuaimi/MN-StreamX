package com.yourapp.iptv.di

import android.content.Context
import com.yourapp.iptv.data.datastore.OrganizationDataStore
import com.yourapp.iptv.data.datastore.organizationStore
import com.yourapp.iptv.domain.repository.OrganizationRepository
import com.yourapp.iptv.domain.repository.SettingsRepository
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