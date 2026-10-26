package com.yourapp.iptv.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NavigationRouteCompatibilityTest {
    /** Route strings determine Navigation destination IDs used in saved back stacks. */
    @Test fun existingPlayerAndGuideRoutesStayStableAcrossThisUpdate() {
        assertEquals("player/{channelId}?group={group}&query={query}&favoritesOnly={favoritesOnly}", Destinations.PLAYER)
        assertEquals("epg?channelId={channelId}&group={group}&query={query}&favoritesOnly={favoritesOnly}", Destinations.EPG)
        assertNotEquals(Destinations.PLAYER, Destinations.HISTORY_PLAYER)
        assertEquals("vod-play/{kind}/{id}/{ext}?title={title}&sourceKey={sourceKey}",Destinations.VOD_PLAYER)
        assertNotEquals(Destinations.VOD_PLAYER,Destinations.VOD_EPISODE_PLAYER)
    }
}