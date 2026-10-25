package com.yourapp.iptv.ui.component.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.yourapp.iptv.domain.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Captures only the already-playing video. Never starts a second decoder or stream. */
@androidx.annotation.OptIn(UnstableApi::class)
internal object LiveFrameCapture {
    private const val WIDTH = 640
    private const val HEIGHT = 360
    private const val TIMEOUT_MS = 2500L
    private val handler = Handler(Looper.getMainLooper())

    suspend fun saveIfNeeded(context: Context, channel: Channel, playerView: PlayerView): FrameCaptureResult {
        if (ChannelFrameStore.fileFor(context, channel) != null) return FrameCaptureResult.ALREADY_CACHED
        if (!playerView.isAttachedToWindow) return FrameCaptureResult.SURFACE_NOT_READY
        val videoView = playerView.videoSurfaceView ?: return FrameCaptureResult.SURFACE_NOT_READY
        val activity = findActivity(playerView.context)
        if (activity?.window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0) {
            return FrameCaptureResult.PROTECTED_OR_INVALID
        }
        val direct = when (videoView) {
            is SurfaceView -> copySurface(videoView)
            is TextureView -> copyTexture(videoView)
            else -> CopyResult(FrameCaptureResult.SURFACE_NOT_READY)
        }
        // A successful PixelCopy may still be a black decoder frame. In that case
        // try the window path before deciding no usable artwork exists.
        val directFrame = direct.bitmap?.takeIf(::containsPicture)
        if (directFrame == null && direct.bitmap != null && !direct.bitmap.isRecycled) {
            direct.bitmap.recycle()
        }
        val outcome = if (directFrame != null) CopyResult(FrameCaptureResult.SAVED, directFrame) else {
            val fallback = activity?.window?.let { copyWindow(it, videoView) }
            if (fallback?.bitmap != null) fallback else {
                return if (direct.bitmap != null) FrameCaptureResult.EMPTY_IMAGE
                    else fallback?.status ?: direct.status
            }
        }
        val bitmap = outcome.bitmap ?: return outcome.status
        if (directFrame == null) Log.i("MNX.FrameCapture", "using window fallback")
        try {
            if (!containsPicture(bitmap)) return FrameCaptureResult.EMPTY_IMAGE
            return if (ChannelFrameStore.save(context, channel, bitmap)) FrameCaptureResult.SAVED
                else FrameCaptureResult.SAVE_FAILED
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private data class CopyResult(val status: FrameCaptureResult, val bitmap: Bitmap? = null)

    private suspend fun copySurface(source: SurfaceView): CopyResult {
        if (!source.isAttachedToWindow || !source.holder.surface.isValid ||
            source.width <= 0 || source.height <= 0) return CopyResult(FrameCaptureResult.SURFACE_NOT_READY)
        return requestCopy { bitmap, callback -> PixelCopy.request(source, bitmap, callback, handler) }
    }

    private suspend fun copyTexture(source: TextureView): CopyResult = withContext(Dispatchers.Main.immediate) {
        if (!source.isAttachedToWindow || !source.isAvailable || source.width <= 0 || source.height <= 0) {
            return@withContext CopyResult(FrameCaptureResult.SURFACE_NOT_READY)
        }
        val bitmap = try { source.getBitmap(WIDTH, HEIGHT) }
            catch (error: RuntimeException) {
                Log.w("MNX.FrameCapture", "TextureView copy failed", error)
                null
            } catch (_: OutOfMemoryError) { null }
        CopyResult(if (bitmap == null) FrameCaptureResult.COPY_FAILED else FrameCaptureResult.SAVED, bitmap)
    }

    private suspend fun copyWindow(window: Window, videoView: View): CopyResult {
        if (!videoView.isAttachedToWindow || videoView.width <= 0 || videoView.height <= 0) {
            return CopyResult(FrameCaptureResult.SURFACE_NOT_READY)
        }
        val rect = Rect()
        if (!videoView.getGlobalVisibleRect(rect) || rect.isEmpty) {
            return CopyResult(FrameCaptureResult.SURFACE_NOT_READY)
        }
        val decor = window.decorView
        val offset = IntArray(2)
        decor.getLocationOnScreen(offset)
        rect.offset(-offset[0], -offset[1])
        if (!rect.intersect(0, 0, decor.width, decor.height) || rect.isEmpty) {
            return CopyResult(FrameCaptureResult.SURFACE_NOT_READY)
        }
        return requestCopy { bitmap, callback -> PixelCopy.request(window, rect, bitmap, callback, handler) }
    }

    private suspend fun requestCopy(request: (Bitmap, PixelCopy.OnPixelCopyFinishedListener) -> Unit): CopyResult {
        val result = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine<CopyResult> { continuation ->
                val bitmap = try { Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888) }
                    catch (_: RuntimeException) { null }
                    catch (_: OutOfMemoryError) { null }
                if (bitmap == null) {
                    continuation.resume(CopyResult(FrameCaptureResult.COPY_FAILED))
                    return@suspendCancellableCoroutine
                }
                try {
                    request(bitmap, PixelCopy.OnPixelCopyFinishedListener { status ->
                        if (continuation.isActive && status == PixelCopy.SUCCESS) {
                            continuation.resume(CopyResult(FrameCaptureResult.SAVED, bitmap))
                        } else {
                            if (!bitmap.isRecycled) bitmap.recycle()
                            if (continuation.isActive) continuation.resume(CopyResult(fromPixelCopyStatus(status)))
                        }
                    })
                } catch (error: RuntimeException) {
                    if (!bitmap.isRecycled) bitmap.recycle()
                    Log.w("MNX.FrameCapture", "PixelCopy rejected request", error)
                    if (continuation.isActive) continuation.resume(CopyResult(FrameCaptureResult.COPY_FAILED))
                }
            }
        }
        return result ?: CopyResult(FrameCaptureResult.SURFACE_NOT_READY)
    }

    private fun fromPixelCopyStatus(status: Int) = when (status) {
        PixelCopy.ERROR_SOURCE_NO_DATA, PixelCopy.ERROR_TIMEOUT,
        PixelCopy.ERROR_SOURCE_INVALID -> FrameCaptureResult.SURFACE_NOT_READY
        else -> FrameCaptureResult.COPY_FAILED
    }

    private fun findActivity(context: Context): Activity? {
        var current = context
        repeat(12) {
            (current as? Activity)?.let { return it }
            current = (current as? ContextWrapper)?.baseContext ?: return null
        }
        return null
    }

    /** Reject blank or uniform surfaces without inspecting or retaining stream metadata. */
    private fun containsPicture(bitmap: Bitmap): Boolean {
        var bright = 0
        var varied = 0
        var first = -1
        for (y in 1..7) for (x in 1..11) {
            val pixel = bitmap.getPixel(x * bitmap.width / 12, y * bitmap.height / 8)
            val r = (pixel ushr 16) and 255
            val g = (pixel ushr 8) and 255
            val b = pixel and 255
            if (r + g + b > 75) bright++
            if (first < 0) first = pixel else if (pixel != first) varied++
        }
        return bright >= 6 && varied >= 6
    }
}