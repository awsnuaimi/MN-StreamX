package com.yourapp.iptv.data.repository

import com.yourapp.iptv.data.source.epg.XtreamEpgCache
import com.yourapp.iptv.data.source.xtream.XtreamUrls
import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.PlaylistSource
import com.yourapp.iptv.domain.model.Program
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class XtreamEpgRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val account = AppSettings(xtreamServer = "https://provider.test/panel", xtreamUsername = "u+ &",
        xtreamPassword = "synthetic/p?&", source = PlaylistSource.XTREAM)
    private val start = 1700000000000L
    private val programs = listOf(Program("News", start - 1000, start + 60_000))

    @Test fun persistedFreshDataAvoidsTheNetworkAfterRepositoryRecreation() = runTest {
        val folder = temporary.newFolder()
        var calls = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = XtreamEpgRepository(XtreamEpgCache(folder), { url ->
            calls++
            assertEquals("get_short_epg", url.queryParameter("action"))
            assertEquals("123", url.queryParameter("stream_id"))
            assertEquals(account.xtreamPassword, url.queryParameter("password"))
            programs
        }, { start }, dispatcher)
        assertEquals(programs, first.load(channel(account), account)?.programs)
        var restoredCalls = 0
        val restored = XtreamEpgRepository(XtreamEpgCache(folder), { restoredCalls++; programs },
            { start + 10_000 }, dispatcher)
        assertEquals(programs, restored.load(channel(account), account)?.programs)
        assertEquals(1, calls)
        assertEquals(0, restoredCalls)
        val saved = folder.listFiles()!!.single().readText()
        assertFalse(saved.contains(account.xtreamPassword))
        assertFalse(saved.contains(account.xtreamUsername))
    }

    @Test fun failedRefreshUsesMarkedStaleData() = runTest {
        val folder = temporary.newFolder()
        var now = start
        var fail = false
        val repository = XtreamEpgRepository(XtreamEpgCache(folder), {
            if (fail) error("Synthetic credential-containing failure") else programs
        }, { now }, StandardTestDispatcher(testScheduler))
        repository.load(channel(account), account)
        now += 61_000
        fail = true
        val saved = repository.load(channel(account), account)!!
        assertTrue(saved.isStale)
        assertEquals(programs, saved.programs)
    }

    @Test fun emptyResponsesAreCachedAndChangingCredentialsUsesADifferentCacheEntry() = runTest {
        var calls = 0
        val repository = XtreamEpgRepository(XtreamEpgCache(temporary.newFolder()), { calls++; emptyList() },
            { start }, StandardTestDispatcher(testScheduler))
        repeat(2) { assertTrue(repository.load(channel(account), account)!!.programs.isEmpty()) }
        assertEquals(1, calls)
        val changed = account.copy(xtreamPassword = "different synthetic secret")
        repository.load(channel(changed), changed)
        assertEquals(2, calls)
    }

    @Test fun m3uAndMismatchedStreamOrAccountNeverSendAnEpgRequest() = runTest {
        val repository = XtreamEpgRepository(XtreamEpgCache(temporary.newFolder()), { error("Unexpected request") },
            { start }, StandardTestDispatcher(testScheduler))
        val valid = channel(account)
        assertNull(repository.load(valid, account.copy(source = PlaylistSource.M3U)))
        assertNull(repository.load(valid.copy(id = "m3u_channel"), account))
        assertNull(repository.load(valid.copy(streamUrl = "https://other.test/live/u/p/123.ts"), account))
        assertNull(repository.load(valid, account.copy(xtreamUsername = "someone else")))
    }

    @Test fun cancellationDoesNotWriteAPartialCache() = runTest {
        val folder = temporary.newFolder()
        val started = CompletableDeferred<Unit>()
        val repository = XtreamEpgRepository(XtreamEpgCache(folder), {
            started.complete(Unit)
            CompletableDeferred<List<Program>>().await()
        }, { start }, StandardTestDispatcher(testScheduler))
        val work = async { repository.load(channel(account), account) }
        started.await()
        work.cancelAndJoin()
        runCurrent()
        assertTrue(work.isCancelled)
        assertTrue(folder.listFiles().orEmpty().isEmpty())
    }

    @Test fun aValidEmptyRefreshReplacesThePreviousPrograms() = runTest {
        var now = start
        var calls = 0
        val repository = XtreamEpgRepository(XtreamEpgCache(temporary.newFolder()), {
            if (++calls == 1) programs else emptyList()
        }, { now }, StandardTestDispatcher(testScheduler))
        repository.load(channel(account), account)
        now += 61_000
        val refreshed = repository.load(channel(account), account)!!
        assertTrue(refreshed.programs.isEmpty())
        assertFalse(refreshed.isStale)
        repository.load(channel(account), account)
        assertEquals(2, calls)
    }

    @Test fun storageFailuresDoNotDiscardProgramsLoadedFromTheServer() = runTest {
        val repository = XtreamEpgRepository(XtreamEpgCache(temporary.newFile()), { programs },
            { start }, StandardTestDispatcher(testScheduler))
        val loaded = repository.load(channel(account), account)!!
        assertEquals(programs, loaded.programs)
        assertFalse(loaded.isStale)
    }

    private fun channel(settings: AppSettings): Channel {
        val urls = XtreamUrls(settings.xtreamServer, settings.xtreamUsername, settings.xtreamPassword)
        return Channel(urls.channelId("123"), "News", streamUrl = urls.stream("123", "ts"))
    }
}