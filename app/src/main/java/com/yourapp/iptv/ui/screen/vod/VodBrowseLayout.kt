package com.yourapp.iptv.ui.screen.vod

/**
 * Preview must not crowd out the browsable poster grid on compact TVs.
 * Dimensions are in dp; the parent Row owns the remaining grid width.
 * A null width means details remain reachable through each poster card.
 */
internal fun vodSeriesPreviewWidthDp(availableDp: Float): Float? {
    if (!availableDp.isFinite() || availableDp < 680f) return null
    return (availableDp * 0.30f).coerceIn(210f, 340f)
}

/** Hero belongs to the lazy catalog header; height stays bounded on compact TVs. */
internal fun vodMovieHeroHeightDp(availableDp: Float): Float = when {
    !availableDp.isFinite() || availableDp < 480f -> 156f
    availableDp < 760f -> 184f
    else -> 208f
}

/** Layout decisions do not depend on Android APIs and can be regression-tested on the JVM. */
internal enum class VodToolbarMode { WIDE, STACKED, NARROW }

internal fun vodToolbarMode(availableDp: Float): VodToolbarMode = when {
    !availableDp.isFinite() || availableDp < 430f -> VodToolbarMode.NARROW
    availableDp < 760f -> VodToolbarMode.STACKED
    else -> VodToolbarMode.WIDE
}