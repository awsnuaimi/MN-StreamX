package com.yourapp.iptv.navigation

import android.app.Application
import android.net.Uri
import com.yourapp.iptv.domain.model.VodKind
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class)
class VodRoutesTest {
    @Test fun movieAndLegacyEpisodeRoutesKeepTheirExistingPaths() {
        assertEquals("vod-play/movie/10/mp4?title=Movie",vodPlaybackRoute(VodKind.MOVIE,"10","mp4","Movie"))
        assertTrue(vodPlaybackRoute(VodKind.SERIES,"10","mp4","Episode").startsWith("vod-play/series/10/"))
    }
    @Test fun seriesContextAndSourceAreEncodedWithoutTurningTitlesIntoNavigationArguments() {
        val route = vodPlaybackRoute(VodKind.SERIES,"10","mkv","Folge & seriesId=99", "a".repeat(64),"55")
        assertTrue(route.startsWith("vod-episode/series/10/mkv?"))
        val uri = Uri.parse("https://synthetic.invalid/$route")
        assertEquals("55",uri.getQueryParameter("seriesId"))
        assertEquals("a".repeat(64),uri.getQueryParameter("sourceKey"))
        assertEquals("Folge & seriesId=99",uri.getQueryParameter("title"))
    }
}