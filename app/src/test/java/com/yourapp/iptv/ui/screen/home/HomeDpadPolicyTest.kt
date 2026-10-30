package com.yourapp.iptv.ui.screen.home

import org.junit.Assert.*
import org.junit.Test

class HomeDpadPolicyTest {
    @Test fun sidebarDirectionDependsOnItsPhysicalSide() {
        assertTrue(HomeDpadPolicy.towardSidebar(HomeDpad.LEFT, false))
        assertFalse(HomeDpadPolicy.towardSidebar(HomeDpad.RIGHT, false))
        assertTrue(HomeDpadPolicy.towardSidebar(HomeDpad.RIGHT, true))
        assertFalse(HomeDpadPolicy.towardSidebar(HomeDpad.LEFT, true))
    }
    @Test fun verticalMovementIsIndependentOfTextDirection() {
        for (rtl in listOf(false, true)) {
            assertEquals(1, HomeDpadPolicy.neighbor(2, 7, HomeDpad.UP, true, rtl))
            assertEquals(3, HomeDpadPolicy.neighbor(2, 7, HomeDpad.DOWN, true, rtl))
        }
    }
    @Test fun firstAndLastItemsNeverWrap() {
        assertNull(HomeDpadPolicy.neighbor(0, 7, HomeDpad.UP, true))
        assertNull(HomeDpadPolicy.neighbor(6, 7, HomeDpad.DOWN, true))
        assertNull(HomeDpadPolicy.neighbor(0, 1, HomeDpad.LEFT, false))
        assertNull(HomeDpadPolicy.neighbor(0, 1, HomeDpad.RIGHT, false))
    }
    @Test fun anEmptyOrRemovedTargetHasNoNeighbor() {
        assertNull(HomeDpadPolicy.neighbor(0, 0, HomeDpad.DOWN, true))
        assertNull(HomeDpadPolicy.neighbor(-1, 4, HomeDpad.RIGHT, false))
        assertNull(HomeDpadPolicy.neighbor(4, 4, HomeDpad.LEFT, false))
    }
    @Test fun horizontalButtonsFollowTheirActualTextLayout() {
        assertEquals(1, HomeDpadPolicy.neighbor(0, 3, HomeDpad.RIGHT, false))
        assertEquals(1, HomeDpadPolicy.neighbor(0, 3, HomeDpad.LEFT, false, true))
        assertEquals(0, HomeDpadPolicy.neighbor(1, 3, HomeDpad.LEFT, false))
        assertEquals(0, HomeDpadPolicy.neighbor(1, 3, HomeDpad.RIGHT, false, true))
    }
    @Test fun horizontalAndVerticalRegionsDoNotInventDiagonalNeighbors() {
        assertNull(HomeDpadPolicy.neighbor(1, 3, HomeDpad.DOWN, false))
        assertNull(HomeDpadPolicy.neighbor(1, 3, HomeDpad.LEFT, true))
    }
    @Test fun stackedDetailsUseDownAndUpBetweenTheirActions() {
        for (rtl in listOf(false, true)) for (side in listOf(false, true)) {
            assertEquals(HomeDpadPolicy.DetailTarget.OTHER, HomeDpadPolicy.detail(HomeDpad.DOWN, false, true, rtl, side))
            assertEquals(HomeDpadPolicy.DetailTarget.OTHER, HomeDpadPolicy.detail(HomeDpad.UP, true, true, rtl, side))
            assertEquals(HomeDpadPolicy.DetailTarget.HEADER, HomeDpadPolicy.detail(HomeDpad.UP, false, true, rtl, side))
            assertEquals(HomeDpadPolicy.DetailTarget.STAY, HomeDpadPolicy.detail(HomeDpad.DOWN, true, true, rtl, side))
        }
    }
    @Test fun stackedDetailsReturnTowardTheListRegardlessOfLanguage() {
        for (rtl in listOf(false, true)) for (translation in listOf(false, true)) for (side in listOf(false, true)) {
            val toward = if (side) HomeDpad.RIGHT else HomeDpad.LEFT
            val away = if (side) HomeDpad.LEFT else HomeDpad.RIGHT
            assertEquals(HomeDpadPolicy.DetailTarget.LIST, HomeDpadPolicy.detail(toward, translation, true, rtl, side))
            assertEquals(HomeDpadPolicy.DetailTarget.STAY, HomeDpadPolicy.detail(away, translation, true, rtl, side))
        }
    }
    @Test fun inlineActionsPreferTheirSiblingBeforeLeavingThePanel() {
        assertEquals(HomeDpadPolicy.DetailTarget.OTHER, HomeDpadPolicy.detail(HomeDpad.RIGHT, false, false, false, true))
        assertEquals(HomeDpadPolicy.DetailTarget.LIST, HomeDpadPolicy.detail(HomeDpad.RIGHT, true, false, false, true))
        assertEquals(HomeDpadPolicy.DetailTarget.OTHER, HomeDpadPolicy.detail(HomeDpad.LEFT, false, false, true, false))
        assertEquals(HomeDpadPolicy.DetailTarget.LIST, HomeDpadPolicy.detail(HomeDpad.LEFT, true, false, true, false))
    }
    @Test fun exhaustiveNeighborsStayInsideTheCurrentRegionAndReverse() {
        for (count in 1..40) for (index in 0 until count) for (rtl in listOf(false, true)) {
            val right = HomeDpadPolicy.neighbor(index, count, HomeDpad.RIGHT, false, rtl)
            val left = HomeDpadPolicy.neighbor(index, count, HomeDpad.LEFT, false, rtl)
            right?.let { assertTrue(it in 0 until count); assertEquals(index, HomeDpadPolicy.neighbor(it, count, HomeDpad.LEFT, false, rtl)) }
            left?.let { assertTrue(it in 0 until count); assertEquals(index, HomeDpadPolicy.neighbor(it, count, HomeDpad.RIGHT, false, rtl)) }
        }
    }
}