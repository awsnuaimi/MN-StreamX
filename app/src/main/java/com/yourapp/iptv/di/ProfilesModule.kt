package com.yourapp.iptv.di

import android.content.Context
import com.yourapp.iptv.data.datastore.*
import com.yourapp.iptv.data.local.AppDatabase
import com.yourapp.iptv.data.profile.*
import com.yourapp.iptv.domain.parental.ParentalControl
import com.yourapp.iptv.domain.repository.ProfileRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object ProfilesModule {
    @Provides @Singleton
    fun profiles(@ApplicationContext context: Context, @DeviceParental pin: ParentalControl,
        database: AppDatabase): ProfileRepository {
        val app = context.applicationContext
        val cleaner = ProfileCleaner(database.profileFavoriteDao(),listOf(app.watchHistoryStore,app.vodProgressStore,
            app.searchHistoryStore,app.homeSessionStore,app.organizationStore))
        return StoredProfiles(app.profilesStore,DeviceProfileCipher(),pin,CoroutineScope(SupervisorJob() + Dispatchers.IO),
            seed = { HomeAppearanceCodec.decode(app.homeAppearanceStore.data.first()) to
                PlayerAppearanceCodec.decode(app.playerAppearanceStore.data.first()) },purge = cleaner::purge)
    }
}