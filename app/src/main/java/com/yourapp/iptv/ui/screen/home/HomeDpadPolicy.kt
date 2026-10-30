package com.yourapp.iptv.ui.screen.home

internal enum class HomeDpad { UP, DOWN, LEFT, RIGHT }

/** Absolute screen directions; text direction and the rail's position are independent. */
internal object HomeDpadPolicy {
    fun towardSidebar(direction: HomeDpad, sidebarOnRight: Boolean) =
        direction == if (sidebarOnRight) HomeDpad.RIGHT else HomeDpad.LEFT

    fun neighbor(index: Int, count: Int, direction: HomeDpad, vertical: Boolean, rtl: Boolean = false): Int? {
        if (index !in 0 until count) return null
        val delta = if (vertical) when (direction) {
            HomeDpad.UP -> -1; HomeDpad.DOWN -> 1; else -> return null
        } else when (direction) {
            HomeDpad.LEFT -> if (rtl) 1 else -1
            HomeDpad.RIGHT -> if (rtl) -1 else 1
            else -> return null
        }
        return (index + delta).takeIf { it in 0 until count }
    }

    enum class DetailTarget { OTHER, LIST, HEADER, STAY }
    fun detail(direction: HomeDpad, translation: Boolean, stacked: Boolean, rtl: Boolean,
        sidebarOnRight: Boolean): DetailTarget {
        val sibling = if (stacked) {
            if (translation) HomeDpad.UP else HomeDpad.DOWN
        } else if (translation != rtl) HomeDpad.LEFT else HomeDpad.RIGHT
        return when {
            direction == sibling -> DetailTarget.OTHER
            direction == HomeDpad.UP -> DetailTarget.HEADER
            towardSidebar(direction, sidebarOnRight) -> DetailTarget.LIST
            else -> DetailTarget.STAY
        }
    }
}