package mnstudio.apps.mnstreamx.ui.component.player

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.util.TypedValue
import androidx.media3.ui.CaptionStyleCompat
import mnstudio.apps.mnstreamx.domain.model.CaptionSettings
import mnstudio.apps.mnstreamx.domain.model.CaptionMode
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

@androidx.annotation.OptIn(UnstableApi::class)
internal class RemotePlayerView(context: Context) : PlayerView(context) {
    var onRemoteKey: ((KeyEvent, Boolean) -> Boolean)? = null
    private var appliedCaption: CaptionPresentation? = null

    /** AndroidView.update can run on every buffering/focus state change; restyle only on real changes. */
    fun applyCaptionPresentation(settings: CaptionSettings) {
        val next = CaptionPresentation(settings.mode == CaptionMode.EMBEDDED,
            settings.textSize.sp, settings.shaded)
        if (next == appliedCaption) return
        val subtitle = subtitleView ?: return
        subtitle.visibility = if (next.visible) View.VISIBLE else View.GONE
        subtitle.setApplyEmbeddedStyles(false)
        subtitle.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, next.textSp.toFloat())
        subtitle.setStyle(CaptionStyleCompat(android.graphics.Color.WHITE,
            if (next.shaded) 0xB0000000.toInt() else android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            android.graphics.Color.BLACK, null))
        appliedCaption = next
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (onRemoteKey?.invoke(event, isControllerFullyVisible) == true) return true
        return super.dispatchKeyEvent(event)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerSurfaceHandle {
    internal var view: RemotePlayerView? = null
    internal var cancelRecoveryAction: (() -> Unit)? = null

    fun cancelRecovery() { cancelRecoveryAction?.invoke() }

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
/** Immutable styling key: audio translation settings alone do not redraw embedded subtitles. */
internal data class CaptionPresentation(val visible: Boolean, val textSp: Int, val shaded: Boolean)