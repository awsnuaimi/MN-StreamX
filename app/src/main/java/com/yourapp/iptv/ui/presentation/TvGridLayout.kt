package com.yourapp.iptv.ui.presentation

import kotlin.math.floor
import com.yourapp.iptv.domain.model.HomeChannelLayout

internal data class TvGridRows(val count: Int, val height: Float)

internal object TvGridLayout {
    fun channelColumns(viewport: Float, preferred: Int, fontScale: Float, layout: HomeChannelLayout): Int =
        if (layout == HomeChannelLayout.LIST) 1
        else columns(viewport, preferred, 144f * fontScale.coerceAtLeast(1f), gap = 8f)

    fun channelRows(viewport: Float, fontScale: Float, layout: HomeChannelLayout): TvGridRows =
        rows(viewport, (if (layout == HomeChannelLayout.LIST) 64f else 80f) * fontScale.coerceAtLeast(1f), gap = 8f)

    fun columns(viewport: Float, preferred: Int, minimum: Float, gap: Float = 12f, padding: Float = 4f): Int {
        val available = (viewport - padding * 2).coerceAtLeast(1f)
        return floor((available + gap) / (minimum.coerceAtLeast(1f) + gap)).toInt()
            .coerceAtLeast(1).coerceAtMost(preferred.coerceIn(1, 5))
    }
    fun rows(viewport: Float, minimum: Float, gap: Float = 12f, padding: Float = 4f): TvGridRows {
        val available = (viewport - padding * 2).coerceAtLeast(1f)
        val count = floor((available + gap) / (minimum.coerceAtLeast(1f) + gap)).toInt().coerceAtLeast(1)
        return TvGridRows(count, ((available - gap * (count - 1)) / count).coerceAtLeast(1f))
    }
    fun rowStart(index: Int, columns: Int): Int = index.coerceAtLeast(0) / columns.coerceAtLeast(1) * columns.coerceAtLeast(1)
    fun snapTarget(first: Int, offset: Int, focused: Int, columns: Int, count: Int): Int? {
        if (offset <= 2 || count <= 0) return null
        val start = rowStart(first, columns)
        val target = if (focused >= start && focused < start + columns) start else start + columns
        return target.coerceAtMost(rowStart(count - 1, columns))
    }
}