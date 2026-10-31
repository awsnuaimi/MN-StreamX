package com.yourapp.iptv.ui.screen.home

import org.junit.Assert.*
import org.junit.Test

class LiveTvNavigationPolicyTest {
    @Test fun pageSizeFollowsTheActualViewportAndFontHeight() {
        assertEquals(8, LiveTvNavigationPolicy.visibleRows(394, 44, 5))
        assertEquals(2, LiveTvNavigationPolicy.visibleRows(254, 88, 5))
        assertEquals(1, LiveTvNavigationPolicy.visibleRows(0, 0, -1))
    }
    @Test fun movementNeverWrapsOrOverflows() {
        assertEquals(0, LiveTvNavigationPolicy.target(0, -1, 100))
        assertEquals(99, LiveTvNavigationPolicy.target(99, 8, 100))
        assertEquals(99, LiveTvNavigationPolicy.target(Int.MAX_VALUE, Int.MAX_VALUE, 100))
        assertEquals(-1, LiveTvNavigationPolicy.target(0, 1, 0))
    }
    @Test fun enteringTheListDuringReloadKeepsTheAlreadyQueuedTarget() {
        val nav = LiveTvListNavigation();nav.request("target")
        val revision = nav.requests.value!!.revision;nav.enter()
        assertEquals("target", nav.requests.value?.key);assertTrue(nav.current(revision))
    }
    @Test fun lateCompletionCannotClearTheNewerFocusIntent() {
        val nav = LiveTvListNavigation()
        nav.request("first"); val old = nav.requests.value!!.revision
        nav.request("second"); nav.finish(old)
        assertEquals("second", nav.requests.value?.key)
        nav.cancel(); assertFalse(nav.current(old)); assertNull(nav.requests.value)
    }
    @Test fun learnedEntryOnlySeedsAnUnspecifiedRequestAndNeverReplacesAnExplicitTarget() {
        val nav = LiveTvListNavigation(); nav.enter("learned")
        assertEquals("learned", nav.requests.value?.key)
        nav.request("numeric", token = 9, instant = true)
        val ticket = nav.requests.value!!.revision
        nav.enter("another")
        assertEquals("numeric", nav.requests.value?.key); assertEquals(9L, nav.requests.value?.token)
        assertTrue(nav.current(ticket))
    }
    @Test fun categoryReturnAndViewportHavePriorityOverLearning() {
        val nav = LiveTvListNavigation()
        val position = com.yourapp.iptv.domain.model.HomeGridPosition("anchor", 12, 1)
        nav.origin("browsed", position); nav.categories(); nav.enter("learned")
        assertEquals("browsed", nav.requests.value?.key)
        assertEquals(position, nav.requests.value?.position); assertTrue(nav.requests.value!!.instant)
    }
}