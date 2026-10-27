package com.yourapp.iptv.di

import com.yourapp.iptv.data.repository.VodRepositoryImpl
import com.yourapp.iptv.domain.repository.VodRepository
import dagger.Provides
import com.yourapp.iptv.data.parental.ProtectedVod
import com.yourapp.iptv.domain.parental.ParentalControl
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object VodModule {
    @Provides fun bindVod(impl: VodRepositoryImpl, parental: ParentalControl): VodRepository = ProtectedVod(impl,parental)
}