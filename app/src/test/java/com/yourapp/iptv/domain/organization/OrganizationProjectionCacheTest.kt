package com.yourapp.iptv.domain.organization

import com.yourapp.iptv.domain.model.Channel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OrganizationProjectionCacheTest {
    private val source = listOf(Channel("1", "TVP 1", streamUrl = "https://unit.test/a", group = "PL General"))
    private val config = OrganizationConfig(sourceKey = "account", enabled = true)

    @Test fun concurrentScreensReuseOneProjection() = runTest {
        val cache = OrganizationProjectionCache()
        val results = (1..8).map { async { cache.project(source, config) {} } }.awaitAll()
        results.forEach { assertSame(results.first(), it) }
    }

    @Test fun anotherSourceWithTheSameChannelIdsCannotReuseTheOldUrls() = runTest {
        val cache = OrganizationProjectionCache()
        val first = cache.project(source, config) {}
        val next = source.map { it.copy(streamUrl = "https://unit.test/new-account") }
        val second = cache.project(next, config) {}
        assertNotSame(first, second)
        assertEquals("https://unit.test/new-account", second.channels.single().streamUrl)
    }

    @Test fun manualRulesInvalidateTheProjectionAndOnlyOneEntryIsRetained() = runTest {
        val cache = OrganizationProjectionCache()
        val first = cache.project(source, config) {}
        val manual = config.copy(channelOverrides = mapOf("1" to "german"))
        val edited = cache.project(source, manual) {}
        assertEquals("german", edited.assignments.getValue("1").folderId)
        assertNotSame(first, cache.project(source, config) {})
    }

    @Test fun cancellationCannotPublishAnIncompleteProjection() = runTest {
        val cache = OrganizationProjectionCache()
        var cancelled = false
        try { cache.project(source, config) { throw CancellationException("cancelled") } }
        catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        val valid = cache.project(source, config) {}
        assertEquals("1", valid.channels.single().id)
        assertSame(valid, cache.project(source, config) {})
    }
}