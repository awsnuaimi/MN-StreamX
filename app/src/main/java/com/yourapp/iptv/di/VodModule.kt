package com.yourapp.iptv.di

import com.yourapp.iptv.data.repository.VodRepositoryImpl
import com.yourapp.iptv.domain.repository.VodRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class VodModule {
    @Binds abstract fun bindVod(impl: VodRepositoryImpl): VodRepository
}