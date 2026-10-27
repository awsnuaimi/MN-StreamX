package com.yourapp.iptv.di

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.yourapp.iptv.data.parental.*
import com.yourapp.iptv.domain.parental.ParentalControl
import com.yourapp.iptv.domain.parental.ParentalCatalog
import com.yourapp.iptv.domain.repository.ChannelRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton
import com.yourapp.iptv.domain.repository.ProfileRepository
import com.yourapp.iptv.data.profile.ProfileParentalControl

@Qualifier @Retention(AnnotationRetention.BINARY)
annotation class RawChannelCatalog
@Qualifier @Retention(AnnotationRetention.BINARY)
annotation class DeviceParental

@Module @InstallIn(SingletonComponent::class)
object ParentalModule {
    @Provides @Singleton @DeviceParental
    fun control(@ApplicationContext context: Context): ParentalControl = StoredParentalControl(
        DeviceParentalStorage(File(context.noBackupFilesDir,"parental_v1.bin")),object : ParentalClock {
            override fun elapsed() = SystemClock.elapsedRealtime()
            override fun boot() = try { Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT) }
                catch (_: Exception) { -1 }
        })
    @Provides @Singleton
    fun profileControl(@DeviceParental device: ParentalControl, profiles: ProfileRepository): ParentalControl =
        ProfileParentalControl(device,profiles)
    @Provides @Singleton
    fun catalog(@RawChannelCatalog channels: ChannelRepository, control: ParentalControl): ParentalCatalog =
        RawParentalCatalog(channels,control)
}