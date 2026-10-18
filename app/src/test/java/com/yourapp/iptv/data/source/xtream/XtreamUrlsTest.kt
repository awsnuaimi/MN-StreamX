package com.yourapp.iptv.data.source.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class XtreamUrlsTest {
    @Test fun accountAndServerNamespaceIdsButPasswordRotationDoesNot() {
        val original = XtreamUrls("https://unit.test/", "user", "first").channelId("7")
        assertEquals(original, XtreamUrls("https://unit.test/player_api.php", "user", "second").channelId("7"))
        assertNotEquals(original, XtreamUrls("https://unit.test/", "another", "first").channelId("7"))
        assertNotEquals(original, XtreamUrls("https://other.test/", "user", "first").channelId("7"))
    }

    @Test fun malformedAndNonHttpServersAreRejected() {
        assertFalse(XtreamUrls.isValid("https://"))
        assertFalse(XtreamUrls.isValid("file:///somewhere"))
        assertFalse(XtreamUrls.isValid("https://user:password@unit.test"))
    }

    @Test fun unsafeLogosAreDropped() {
        val urls = XtreamUrls("https://unit.test/", "user", "password")
        assertEquals(null, urls.icon("file:///etc/hosts"))
        assertEquals(null, urls.icon("https://user:secret@unit.test/logo.png"))
    }
}