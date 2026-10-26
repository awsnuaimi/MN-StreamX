package com.yourapp.iptv.di

import android.content.Context
import com.yourapp.iptv.data.diagnostics.AndroidDiagnosticsRepository
import com.yourapp.iptv.domain.repository.DiagnosticsRepository
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