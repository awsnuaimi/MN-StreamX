package com.yourapp.iptv.ui.component.player

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import com.yourapp.iptv.domain.model.Channel
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** A single attempt on the currently visible Live TV surface; no second decoder. */
internal object LiveFrameCapture {

    suspend fun saveIfNeeded(context: Context, channel: Channel, surface: SurfaceView): FrameCaptureResult {
        if (ChannelFrameStore.fileFor(context, channel) != null) return FrameCaptureResult.ALREADY_CACHED
        if (!surface.isAttachedToWindow || !surface.holder.surface.isValid ||
            surface.width <= 0 || surface.height <= 0) return FrameCaptureResult.SURFACE_NOT_READY
        val (status, bitmap) = capture(surface)
        if (bitmap == null) return when (status) {
            PixelCopy.ERROR_SOURCE_INVALID -> FrameCaptureResult.PROTECTED_OR_INVALID
            PixelCopy.ERROR_SOURCE_NO_DATA, PixelCopy.ERROR_TIMEOUT -> FrameCaptureResult.SURFACE_NOT_READY
            else -> FrameCaptureResult.COPY_FAILED
        }
        try {
            if (!containsPicture(bitmap)) return FrameCaptureResult.EMPTY_IMAGE
            return if (ChannelFrameStore.save(context, channel, bitmap)) FrameCaptureResult.SAVED
                else FrameCaptureResult.SAVE_FAILED
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /** PixelCopy callback always owns the bitmap until it returns to the caller. */
    private suspend fun capture(surface: SurfaceView): Pair<Int, Bitmap?> =
        suspendCancellableCoroutine { continuation ->
            val bitmap = try {
                Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
            } catch (_: RuntimeException) {
                continuation.resume(PixelCopy.ERROR_DESTINATION_INVALID to null)
                return@suspendCancellableCoroutine
            }
            try {
                PixelCopy.request(surface, bitmap, { status ->
                    if (status == PixelCopy.SUCCESS && continuation.isActive) {
                        continuation.resume(status to bitmap)
                    } else {
                        if (!bitmap.isRecycled) bitmap.recycle()
                        if (continuation.isActive) continuation.resume(status to null)
                    }
                }, Handler(Looper.getMainLooper()))
            } catch (_: RuntimeException) {
                if (!bitmap.isRecycled) bitmap.recycle()
                if (continuation.isActive) {
                    continuation.resume(PixelCopy.ERROR_UNKNOWN to null)
                }
            }
        }

    /** Reject uniform or nearly-black video surfaces; sample only 35 pixels. */
    private fun containsPicture(bitmap: Bitmap): Boolean {
        var lit = 0
        var varied = 0
        var first = -1
        for (y in 1..5) for (x in 1..7) {
            val pixel = bitmap.getPixel(x * bitmap.width / 8, y * bitmap.height / 6)
            val r = (pixel ushr 16) and 255
            val g = (pixel ushr 8) and 255
            val b = pixel and 255
            if (r + g + b > 75) lit++
            if (first < 0) first = pixel
            else if (pixel != first) varied++
        }
        return lit >= 4 && varied >= 3
    }
}