package mnstudio.apps.mnstreamx.data.image

import android.content.Context
import coil.Coil
import coil.EventListener
import coil.annotation.ExperimentalCoilApi
import coil.decode.DataSource
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import mnstudio.apps.mnstreamx.domain.image.*

internal object ImageDiagnostics {
    val recorder = ImageLoadRecorder()

    /** Called on the repository's IO dispatcher, never by image callbacks or composition. */
    @OptIn(ExperimentalCoilApi::class)
    fun read(context: Context): ImageDiagnosticsSnapshot {
        val loader = Coil.imageLoader(context.applicationContext)
        val memory = cacheUsage { loader.memoryCache?.let { ImageCacheUsage(it.size.toLong(), it.maxSize.toLong()) } }
        val disk = cacheUsage { loader.diskCache?.let { ImageCacheUsage(it.size, it.maxSize) } }
        return ImageDiagnosticsSnapshot(memory, disk, recorder.snapshot())
    }

    private inline fun cacheUsage(read: () -> ImageCacheUsage?): ImageCacheUsage? =
        try { read() } catch (_: Exception) { null }
}

/** One token per execution, including null-data failures and lifecycle/size-resolution waits. */
internal class ImageMetricListener(recorder: ImageLoadRecorder) : EventListener {
    private val token = recorder.begin()
    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        token.finish(when (result.dataSource) {
            DataSource.MEMORY_CACHE -> ImageLoadResult.MEMORY
            DataSource.DISK -> ImageLoadResult.DISK
            DataSource.NETWORK -> ImageLoadResult.NETWORK
            DataSource.MEMORY -> ImageLoadResult.LOCAL
        })
    }
    override fun onError(request: ImageRequest, result: ErrorResult) {
        token.finish(ImageLoadResult.FAILED, ImageFailureClassifier.classify(result.throwable))
    }
    override fun onCancel(request: ImageRequest) { token.finish(ImageLoadResult.CANCELLED) }
}