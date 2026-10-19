package com.yourapp.iptv.ui.component.player

import android.content.Context
import android.view.KeyEvent
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

@androidx.annotation.OptIn(UnstableApi::class)
internal class RemotePlayerView(context: Context) : PlayerView(context) {
    var onRemoteKey: ((KeyEvent, Boolean) -> Boolean)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (onRemoteKey?.invoke(event, isControllerFullyVisible) == true) return true
        return super.dispatchKeyEvent(event)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerSurfaceHandle {
    internal var view: RemotePlayerView? = null

    fun requestFocus() { view?.requestFocus() }
    fun hideControls() { view?.hideController() }

    fun showControls() {
        view?.apply {
            requestFocus()
            showController()
        }
    }

    internal fun detachPlayer() {
        view?.apply {
            player = null
            keepScreenOn = false
        }
    }

    internal fun releaseView(released: RemotePlayerView) {
        released.player = null
        released.keepScreenOn = false
        released.onRemoteKey = null
        released.setControllerVisibilityListener(null as PlayerView.ControllerVisibilityListener?)
        if (view === released) view = null
    }
}