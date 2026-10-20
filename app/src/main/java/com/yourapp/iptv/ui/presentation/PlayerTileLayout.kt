package com.yourapp.iptv.ui.presentation

internal object PlayerTileLayout {
    fun fontScale(value: Float): Float = if (value.isFinite()) value.coerceAtLeast(0.8f) else 1f
    // Header + name are reserved text slots; the playing badge never changes the card layout.
    fun height(showLogo: Boolean, scale: Float): Float =
        ((if (showLogo) 32f else 0f) + 40f * fontScale(scale) + 24f).coerceAtLeast(88f)
}