package com.yourapp.iptv.ui.presentation

import kotlin.math.floor

internal data class TvGridRows(val count: Int, val height: Float)

internal object TvGridLayout {
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