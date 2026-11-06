package mnstudio.apps.mnstreamx.di

import mnstudio.apps.mnstreamx.data.repository.VodRepositoryImpl
import mnstudio.apps.mnstreamx.domain.repository.VodRepository
import dagger.Provides
import mnstudio.apps.mnstreamx.data.parental.ProtectedVod
import mnstudio.apps.mnstreamx.domain.parental.ParentalControl
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object VodModule {
    @Provides fun bindVod(impl: VodRepositoryImpl, parental: ParentalControl): VodRepository = ProtectedVod(impl,parental)
}