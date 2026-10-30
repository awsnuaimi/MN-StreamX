package com.yourapp.iptv.ui.component.common

import android.app.Application
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.activity.ComponentActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FocusInputWindowTest {
    @Test fun keyPressSignalsBeforeDelegationAndReleaseKeepsTheSameEpoch() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            val window = controller.get().window
            val original = window.callback
            val start = FocusInputEpoch.value
            var received: KeyEvent? = null
            window.callback = object : Window.Callback by original {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    received = event
                    assertEquals(start + 1, FocusInputEpoch.value)
                    return true
                }
            }
            val bridge = requireNotNull(FocusInputWindow.install(window))
            val press = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)
            assertTrue(window.callback.dispatchKeyEvent(press)); assertSame(press, received)
            val release = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN)
            assertTrue(window.callback.dispatchKeyEvent(release)); assertSame(release, received)
            assertEquals(start + 1, FocusInputEpoch.value)
            bridge.close(window)
        }
    }
    @Test fun touchPressSignalsOnceAndPreservesTheDelegatesResult() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            val window = controller.get().window
            val original = window.callback
            var received: MotionEvent? = null
            window.callback = object : Window.Callback by original {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean { received = event;return false }
            }
            val bridge = requireNotNull(FocusInputWindow.install(window))
            val start = FocusInputEpoch.value
            val press = MotionEvent.obtain(1, 1, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
            val move = MotionEvent.obtain(1, 2, MotionEvent.ACTION_MOVE, 12f, 12f, 0)
            try {
                assertFalse(window.callback.dispatchTouchEvent(press)); assertSame(press, received)
                assertFalse(window.callback.dispatchTouchEvent(move)); assertSame(move, received)
                assertEquals(start + 1, FocusInputEpoch.value)
            } finally { press.recycle();move.recycle();bridge.close(window) }
        }
    }
    @Test fun installingTwiceKeepsOneBridgeAndClosingRestoresItsOwner() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            val window = controller.get().window
            val original = window.callback
            val bridge = requireNotNull(FocusInputWindow.install(window))
            assertSame(bridge, FocusInputWindow.install(window))
            bridge.close(window);bridge.close(window)
            assertSame(original, window.callback)
        }
    }
    @Test fun closingDoesNotReplaceACallbackInstalledByAnotherOwner() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            val window = controller.get().window
            val bridge = requireNotNull(FocusInputWindow.install(window))
            val later = object : Window.Callback by window.callback {}
            window.callback = later
            bridge.close(window)
            assertSame(later, window.callback)
        }
    }
}