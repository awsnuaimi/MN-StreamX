package com.yourapp.iptv.domain.model

import org.junit.Assert.*
import org.junit.Test

class HomeStartupPolicyTest {
    @Test fun ageClockChangesAndUnknownRefreshTimesAreHandled() {
        val now = 5_000_000L
        assertFalse(HomeStartupPolicy.needsRefresh(now, now))
        assertFalse(HomeStartupPolicy.needsRefresh(now - HomeStartupPolicy.FRESH_FOR_MS + 1, now))
        assertTrue(HomeStartupPolicy.needsRefresh(now - HomeStartupPolicy.FRESH_FOR_MS, now))
        assertTrue(HomeStartupPolicy.needsRefresh(0, now))
        assertTrue(HomeStartupPolicy.needsRefresh(now + 1, now))
    }
}