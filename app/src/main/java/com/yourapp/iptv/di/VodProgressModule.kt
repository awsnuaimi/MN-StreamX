package com.yourapp.iptv.di

import android.content.Context
import com.yourapp.iptv.domain.repository.ProfileRepository
import com.yourapp.iptv.data.datastore.VodProgressDataStore
import com.yourapp.iptv.data.datastore.vodProgressStore
import com.yourapp.iptv.domain.repository.SettingsRepository
import com.yourapp.iptv.domain.repository.VodProgressRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton
import com.yourapp.iptv.data.parental.ProtectedProgress
import com.yourapp.iptv.domain.parental.ParentalControl

@Module
@InstallIn(SingletonComponent::class)
object VodProgressModule {
    @Provides @Singleton
    fun provideProgress(@ApplicationContext context: Context, settings: SettingsRepository,
        parental: ParentalControl, profiles: ProfileRepository): VodProgressRepository =
        ProtectedProgress(VodProgressDataStore(context.applicationContext.vodProgressStore, settings,
            CoroutineScope(SupervisorJob() + Dispatchers.IO), profiles = profiles),parental)
}