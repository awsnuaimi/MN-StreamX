package com.yourapp.iptv.di

import android.content.Context
import android.os.Process
import com.yourapp.iptv.data.caption.*
import com.yourapp.iptv.data.datastore.CaptionDataStore
import com.yourapp.iptv.data.datastore.captionStore
import com.yourapp.iptv.domain.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class CaptionWorker
@Module @InstallIn(SingletonComponent::class)
object CaptionModule {
    @Provides @Singleton fun preferences(@ApplicationContext context: Context): CaptionRepository = CaptionDataStore(context.captionStore)
    @Provides @Singleton internal fun files(@ApplicationContext context: Context) = VerifiedModelFiles(
        context.noBackupFilesDir.resolve("caption-models"), OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build())
    @Provides @Singleton internal fun models(files: VerifiedModelFiles): CaptionModels = object : CaptionModels {
        override suspend fun prepare(progress: (Int) -> Unit) = files.prepare(CaptionModelCatalog.speech, progress)
            .resolve("ggml-tiny-q5_1.bin")
    }
    @Provides @Singleton fun device(@ApplicationContext context: Context): CaptionDevice = CaptionDeviceInfo(context)
    @Provides @Singleton fun speech(): SpeechEngineFactory = SpeechEngineFactory { WhisperSpeechEngine(it) }
    @Provides @Singleton internal fun translators(files: VerifiedModelFiles, device: CaptionDevice): CaptionTranslatorFactory =
        CaptionTranslatorFactory { OnnxCaptionTranslator(files, device) }
    // One application-owned low-priority worker bounds native concurrency across navigation destinations.
    @Provides @Singleton @CaptionWorker fun worker(): CoroutineDispatcher = Executors.newSingleThreadExecutor { action ->
        Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); action.run() }, "caption-worker")
    }.asCoroutineDispatcher()
}