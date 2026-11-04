package com.yourapp.iptv.data.image

import android.app.ActivityManager
import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.yourapp.iptv.domain.image.ImageLoadRecorder
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Coil owns this loader through the Application factory, across all Compose destinations. */
internal object AppImageLoader {
    fun create(context: Context, recorder: ImageLoadRecorder = ImageDiagnostics.recorder): ImageLoader {
        val app = context.applicationContext
        val manager = app.getSystemService(ActivityManager::class.java)
        val budget = ImageCacheBudget.forDevice(manager?.memoryClass ?: 128, manager?.isLowRamDevice ?: true)
        return ImageLoader.Builder(app)
            .eventListenerFactory { ImageMetricListener(recorder) }
            .memoryCache { MemoryCache.Builder(app).maxSizeBytes(budget.memoryBytes).build() }
            .diskCache {
                // Keep Coil 2's existing cache directory/format so updates can reuse saved images.
                DiskCache.Builder().directory(app.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(budget.diskBytes).build()
            }
            .okHttpClient { client(budget) }
            .bitmapFactoryMaxParallelism(budget.maxDecodes)
            .respectCacheHeaders(true)
            .crossfade(false)
            .build()
    }

    /** A separate dispatcher prevents artwork queues from competing with catalog/player calls. */
    private fun client(budget: ImageCacheBudget): OkHttpClient = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply {
            maxRequests = budget.maxRequests
            maxRequestsPerHost = budget.maxRequestsPerHost
        })
        .connectionPool(ConnectionPool(budget.maxRequestsPerHost, 2, TimeUnit.MINUTES))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
}