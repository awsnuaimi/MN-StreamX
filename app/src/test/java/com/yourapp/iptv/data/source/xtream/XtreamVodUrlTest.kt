package com.yourapp.iptv.data.source.xtream

import com.yourapp.iptv.domain.model.VodKind
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class XtreamVodUrlTest {
    @Test fun movieAndEpisodeUrlsRespectServerSubpathsAndCredentialEncoding() {
        val urls = XtreamUrls("https://example.org/panel/player_api.php", "u+&", "secret/a")
        val film = urls.vodStream(VodKind.MOVIE, "10", "mkv").toHttpUrl()
        val episode = urls.vodStream(VodKind.SERIES, "55", "m3u8").toHttpUrl()
        assertEquals(listOf("panel", "movie", "u+&", "secret/a", "10.mkv"), film.pathSegments)
        assertEquals(listOf("panel", "series", "u+&", "secret/a", "55.m3u8"), episode.pathSegments)
    }

    @Test fun detailRequestsUseCorrectQueryKey() {
        val urls = XtreamUrls("https://example.org", "u", "p")
        assertEquals("12", urls.vodApi("get_vod_info", "12").queryParameter("vod_id"))
        assertEquals("77", urls.vodApi("get_series_info", "77").queryParameter("series_id"))
    }

    @Test fun unsafeIdsAreRejected() {
        val urls = XtreamUrls("https://example.org", "u", "p")
        assertTrue(runCatching { urls.vodStream(VodKind.SERIES, "../1", "mp4") }.isFailure)
        assertTrue(runCatching { urls.vodApi("get_vod_info", "7/2") }.isFailure)
        assertTrue(runCatching { urls.vodApi("get_vod_streams", "1") }.isFailure)
    }

    @Test fun vodCredentialsAreStrictlyEncodedOnceWithoutChangingTheirValue() {
        val url = XtreamUrls("https://example.org", "u+&", "p/a%?é")
            .vodStream(VodKind.MOVIE,"7","m4v").toHttpUrl()
        assertEquals("/movie/u%2B%26/p%2Fa%25%3F%C3%A9/7.m4v",url.encodedPath)
        assertEquals(listOf("movie","u+&","p/a%?é","7.m4v"),url.pathSegments)
    }

    @Test fun directTargetsPreserveSignedQueriesWithoutAddingAccountCredentials() {
        val urls = XtreamUrls("https://example.org/panel", "u", "p")
        assertEquals("https://cdn.test/play?token=a%2Bb&sig=x%26y",
            urls.directVodSource("https://cdn.test/play?token=a%2Bb&sig=x%26y#ignored"))
        assertEquals("https://example.org/media/7.mkv?token=x",urls.directVodSource("/media/7.mkv?token=x"))
        for (value in listOf("0","false","null","file:///tmp/x","javascript:alert(1)",
            "https://user:pass@cdn.test/play","https://cdn.test/a\r\nb")) assertNull(urls.directVodSource(value))
    }
}