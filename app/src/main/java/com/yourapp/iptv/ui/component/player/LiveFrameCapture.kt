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

/** Captures from the existing player surface; never starts another decoder or stream. */
internal object LiveFrameCapture {
    suspend fun saveIfNeeded(context: Context, channel: Channel, surface: SurfaceView) {
        if (ChannelFrameStore.fileFor(context, channel) != null) return
        if (!surface.holder.surface.isValid || surface.width <= 0 || surface.height <= 0) return
        val bitmap = capture(surface) ?: return
        try {
            if (containsPicture(bitmap)) ChannelFrameStore.save(context, channel, bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun capture(surface: SurfaceView): Bitmap? =
        suspendCancellableCoroutine { result ->
            val bitmap = try {
                Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
            } catch (_: RuntimeException) {
                result.resume(null)
                return@suspendCancellableCoroutine
            }
            try {
                PixelCopy.request(surface, bitmap, { status ->
                    if (status == PixelCopy.SUCCESS && result.isActive) {
                        result.resume(bitmap)
                    } else {
                        bitmap.recycle()
                        if (result.isActive) result.resume(null)
                    }
                }, Handler(Looper.getMainLooper()))
            } catch (_: RuntimeException) {
                bitmap.recycle()
                if (result.isActive) result.resume(null)
            }
        }

    /** Reject nearly uniform black/protected surfaces without processing every pixel. */
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