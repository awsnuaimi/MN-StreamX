package mnstudio.apps.mnstreamx.ui.presentation

internal object PlayerTileLayout {
    fun fontScale(value: Float): Float = if (value.isFinite()) value.coerceAtLeast(0.8f) else 1f
    fun height(showLogo: Boolean, scale: Float): Float =
        ((if (showLogo) 32f else 0f) + 40f * fontScale(scale) + 24f).coerceAtLeast(88f)
}