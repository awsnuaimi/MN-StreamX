package com.yourapp.iptv.domain.model

import org.junit.Assert.*
import org.junit.Test

class PlaylistCacheKeyTest {
    private val account = AppSettings(xtreamServer = "https://unit.test", xtreamUsername = "user",
        xtreamPassword = "secret", source = PlaylistSource.XTREAM)

    @Test fun everyActiveCredentialSeparatesSnapshots() {
        val original = PlaylistCacheKey.of(account)
        for (changed in listOf(account.copy(xtreamServer = "https://other.test"),
            account.copy(xtreamUsername = "other"), account.copy(xtreamPassword = "other"),
            account.copy(source = PlaylistSource.M3U, m3uUrl = "https://unit.test/list"))) {
            assertNotEquals(original, PlaylistCacheKey.of(changed))
        }
        assertTrue(original.matches(Regex("[0-9a-f]{64}")))
        assertFalse(original.contains("secret"))
    }

    @Test fun epgAndInactiveSourceFieldsDoNotInvalidateTheSnapshot() {
        assertEquals(PlaylistCacheKey.of(account), PlaylistCacheKey.of(account.copy(
            epgUrl = "https://epg.test", m3uUrl = "https://inactive.test")))
        val m3u = AppSettings(m3uUrl = "https://unit.test/a")
        assertNotEquals(PlaylistCacheKey.of(m3u), PlaylistCacheKey.of(m3u.copy(m3uUrl = "https://unit.test/b")))
        assertEquals(PlaylistCacheKey.of(m3u), PlaylistCacheKey.of(m3u.copy(xtreamPassword = "inactive")))
    }

    @Test fun embeddedDelimitersCannotAliasAnotherAccount() {
        assertNotEquals(PlaylistCacheKey.of(account.copy(xtreamUsername = "a\u0000b", xtreamPassword = "c")),
            PlaylistCacheKey.of(account.copy(xtreamUsername = "a", xtreamPassword = "b\u0000c")))
    }
}