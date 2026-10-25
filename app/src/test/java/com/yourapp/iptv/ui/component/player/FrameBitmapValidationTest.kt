package com.yourapp.iptv.ui.component.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.yourapp.iptv.domain.model.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Actual Android pixels: opaque ARGB colors are signed negative Int values. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FrameBitmapValidationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val channel = Channel(id = "copied-frame-test", name = "Test",
        streamUrl = "https://example.invalid/frame-test")

    @Before @After fun clearCache() {
        context.cacheDir.resolve("live-hero-frames-v2").deleteRecursively()
    }

    private fun picture(): Bitmap {
        val width = 96; val height = 64
        val pixels = IntArray(width * height) { index ->
            val alternate = (index % width / 8 + index / width / 8) % 2 == 0
            if (alternate) Color.rgb(210, 45, 30) else Color.rgb(25, 100, 230)
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun accepted(bitmap: Bitmap) = LiveFrameCapture.containsPicture(bitmap)

    @Test fun anOpaqueDetailedVideoFrameMustBeAccepted() {
        val bitmap = picture()
        try {
            for (y in 1..7) for (x in 1..11) {
                assertTrue("Opaque ARGB must exercise negative Int values", bitmap.getPixel(x * 8, y * 8) < 0)
            }
            assertTrue("A bright, varied opaque video frame was rejected", accepted(bitmap))
        } finally { bitmap.recycle() }
    }

    @Test fun whiteFirstPixelMustNotActAsAnUnsetSentinel() {
        val bitmap = picture()
        try {
            bitmap.setPixel(8, 8, Color.WHITE)
            assertEquals(-1, bitmap.getPixel(8, 8))
            assertTrue("Opaque white is a color, not an unset marker", accepted(bitmap))
        } finally { bitmap.recycle() }
    }

    @Test fun uniformBlackAndWhiteStillDoNotQualifyAsPictures() {
        val bitmap = picture()
        try {
            bitmap.eraseColor(Color.BLACK); assertFalse(accepted(bitmap))
            bitmap.eraseColor(Color.WHITE); assertFalse(accepted(bitmap))
        } finally { bitmap.recycle() }
    }

    @Test fun anUninitializedTransparentSurfaceIsRejected() {
        val bitmap = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888)
        try { assertFalse(accepted(bitmap)) } finally { bitmap.recycle() }
    }

    @Test fun oneColoredSampleDoesNotTurnAWhiteSurfaceIntoAPicture() {
        val bitmap = picture()
        try {
            bitmap.eraseColor(Color.WHITE)
            bitmap.setPixel(16, 8, Color.RED)
            assertFalse(accepted(bitmap))
        } finally { bitmap.recycle() }
    }

    @Test fun aCopiedOpaqueFrameIsValidatedSavedAndReleased() = runBlocking {
        val bitmap = picture()
        assertEquals(FrameCaptureResult.SAVED, LiveFrameCapture.saveCopiedFrame(context, channel, bitmap))
        assertTrue(bitmap.isRecycled)
        val file = checkNotNull(ChannelFrameStore.validFileFor(context, channel))
        val decoded = checkNotNull(BitmapFactory.decodeFile(file.absolutePath))
        try {
            assertEquals(96, decoded.width); assertEquals(64, decoded.height)
            assertEquals(255, Color.alpha(decoded.getPixel(8, 8)))
        } finally { decoded.recycle() }
    }

    @Test fun aBlankCopiedFrameIsNotSavedAndIsReleased() = runBlocking {
        val bitmap = picture().apply { eraseColor(Color.BLACK) }
        assertEquals(FrameCaptureResult.EMPTY_IMAGE, LiveFrameCapture.saveCopiedFrame(context, channel, bitmap))
        assertTrue(bitmap.isRecycled)
        assertNull(ChannelFrameStore.fileFor(context, channel))
    }

    @Test fun aCopyFromAnOldSessionCannotBeSavedAndIsReleased() = runBlocking {
        val bitmap = picture()
        assertEquals(FrameCaptureResult.STALE_SESSION,
            LiveFrameCapture.saveCopiedFrame(context, channel, bitmap) { false })
        assertTrue(bitmap.isRecycled)
        assertNull(ChannelFrameStore.fileFor(context, channel))
    }
}