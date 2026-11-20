package mnstudio.apps.mnstreamx.ui.screen.player

/** Pure index mapping for the two independently scrolling channel columns. */
internal data class PlayerChannelColumns(val size: Int, val twoColumns: Boolean) {
    init { require(size >= 0) }

    val firstCount: Int = if (twoColumns && size > 1) size / 2 + size % 2 else size
    val secondCount: Int = size - firstCount

    fun isSecond(index: Int): Boolean = index in firstCount until size && secondCount > 0

    fun localIndex(index: Int): Int = if (isSecond(index)) index - firstCount else index

    fun vertical(index: Int, direction: Int): Int? {
        if (index !in 0 until size || direction !in -1..1) return null
        val next = index + direction
        return next.takeIf { it in 0 until size && isSecond(it) == isSecond(index) }
    }

    fun towardSecond(index: Int): Int? {
        if (index !in 0 until firstCount || secondCount == 0) return null
        return firstCount + index.coerceAtMost(secondCount - 1)
    }

    fun towardFirst(index: Int): Int? =
        if (isSecond(index)) index - firstCount else null

    /** Group -> first channel column, preserving the selected row when possible. */
    fun groupEntry(previousIndex: Int?): Int? {
        if (size == 0) return null
        val index = previousIndex?.takeIf { it in 0 until size } ?: 0
        return if (isSecond(index)) index - firstCount else index
    }
}