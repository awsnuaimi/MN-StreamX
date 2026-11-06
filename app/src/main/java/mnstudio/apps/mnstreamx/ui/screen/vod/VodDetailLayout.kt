package mnstudio.apps.mnstreamx.ui.screen.vod

/** Layout decision independent of Compose: testable without an Android runtime. */
internal object VodDetailLayout {
    // Width after the dialog's safe-area padding, in density-independent pixels.
    fun split(availableWidthDp: Float): Boolean = availableWidthDp.isFinite() && availableWidthDp >= 720f

    fun panelFraction(availableWidthDp: Float): Float = if (split(availableWidthDp)) 0.94f else 1f
}