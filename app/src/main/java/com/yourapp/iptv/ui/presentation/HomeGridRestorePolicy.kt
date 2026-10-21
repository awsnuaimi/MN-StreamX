package com.yourapp.iptv.ui.presentation

internal data class HomeGridTarget(val index: Int, val offset: Int)

internal object HomeGridRestorePolicy {
    fun target(anchor: Int, focused: Int, columns: Int, rows: Int, offset: Int,
        savedColumns: Int): HomeGridTarget {
        val width = columns.coerceAtLeast(1)
        val selectedRow = TvGridLayout.rowStart(focused.coerceAtLeast(0), width)
        val anchorRow = if (anchor < 0) selectedRow else TvGridLayout.rowStart(anchor, width)
        val focusVisible = focused >= anchorRow && focused < anchorRow + width * rows.coerceAtLeast(1)
        val index = if (focusVisible) anchorRow else selectedRow
        return HomeGridTarget(index, if (focusVisible && anchor >= 0 && width == savedColumns)
            offset.coerceIn(0, 100_000) else 0)
    }
}