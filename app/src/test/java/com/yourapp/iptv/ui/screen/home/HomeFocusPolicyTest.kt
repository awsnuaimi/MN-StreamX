package com.yourapp.iptv.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFocusPolicyTest {
    @Test fun savedSectionsRestoreWithoutChangingTheActiveBrowseState() {
        HomeSection.entries.forEach { section ->
            assertEquals(section, HomeFocusPolicy.restore(section.name, false, true))
        }
    }

    @Test fun invalidSavedFocusCannotCrashTheLauncher() {
        assertEquals(HomeSection.DASHBOARD, HomeFocusPolicy.restore(null, false, true))
        assertEquals(HomeSection.DASHBOARD, HomeFocusPolicy.restore("DELETED", false, true))
    }

    @Test fun unavailableActionsFallBackToTheStableDashboardItem() {
        assertEquals(HomeSection.DASHBOARD,
            HomeFocusPolicy.restore(HomeSection.ORGANIZE.name, false, false))
        assertEquals(HomeSection.DASHBOARD,
            HomeFocusPolicy.restore(HomeSection.REFRESH.name, true, true))
        assertEquals(HomeSection.MOVIES,
            HomeFocusPolicy.restore(HomeSection.MOVIES.name, true, false))
    }
}