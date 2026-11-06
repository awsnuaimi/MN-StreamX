package mnstudio.apps.mnstreamx.di

import android.content.Context
import mnstudio.apps.mnstreamx.data.recovery.*
import mnstudio.apps.mnstreamx.domain.recovery.AppDataReset
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.*

@Module @InstallIn(SingletonComponent::class)
object AppResetModule {
    @Provides @Singleton
    fun reset(@ApplicationContext context: Context): AppDataReset = StoredAppDataReset(
        DeviceAppResetJournal(File(context.noBackupFilesDir,"full_reset_v1.pending")),AndroidAppDataEraser(context),
        CoroutineScope(SupervisorJob() + Dispatchers.IO))
}