package mnstudio.apps.mnstreamx.navigation

import mnstudio.apps.mnstreamx.domain.performance.PerformanceScreen
import org.junit.Assert.*
import org.junit.Test

class NavigationPerformanceTest {
    @Test fun templatesMapToFixedNamesAndResolvedArgumentValuesAreNotAccepted() {
        assertEquals(PerformanceScreen.LIVE_PLAYER,performanceScreen(Destinations.HISTORY_PLAYER))
        assertEquals(PerformanceScreen.VOD_PLAYER,performanceScreen(Destinations.VOD_EPISODE_PLAYER))
        assertEquals(PerformanceScreen.HISTORY,performanceScreen(Destinations.HISTORY))
        assertNull(performanceScreen("history-player/private-title?sourceKey=private-secret"))
        assertNull(performanceScreen("https://private.invalid/user/password"));assertNull(performanceScreen(null))
    }
}