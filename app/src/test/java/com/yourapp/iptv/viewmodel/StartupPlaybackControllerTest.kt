package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.WatchHistoryRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupPlaybackControllerTest {
    private val settings = HistorySettings()
    private val history = HistoryMemory()
    private val channels = HistoryChannels()
    private val preference = StartupPreference()
    private val played = mutableListOf<StartupPlaybackTarget>()
    private var persisted = false

    private fun TestScope.controller(restored: Boolean = false) = StartupPlaybackController(
        backgroundScope, preference, settings, history, channels, restored) { persisted = true }
        .also { it.initialize(true) }
    private fun seed(vararg ids: String) {
        history.records(settings.key).value = ids.mapIndexed { index, id -> WatchHistoryEntry(id, 100L - index) }
    }
    private fun ready(controller: StartupPlaybackController, key: String = settings.key) =
        controller.onHomeReady(key) { played += it }

    @Test fun disabledOptionDoesNotReadAnyHistoryOrChannelData() = runTest {
        preference.value.value = false
        val neverRead = object : WatchHistoryRepository by history {
            override fun observe(sourceKey: String) = flow<List<WatchHistoryEntry>> { error("Disabled feature read history") }
        }
        val controller = StartupPlaybackController(backgroundScope, preference, settings, neverRead, channels)
        controller.initialize(true); ready(controller); runCurrent()
        assertTrue(played.isEmpty()); assertEquals(0, channels.lookupReads); assertEquals(0, channels.allReads)
    }

    @Test fun waitsForHomeThenLooksUpOnlyTheLastConfirmedChannelOnce() = runTest {
        seed("b", "a")
        val controller = controller(); runCurrent()
        assertEquals(0, preference.reads); assertEquals(0, channels.lookupReads)
        ready(controller); assertTrue(persisted); runCurrent()
        assertEquals(listOf(StartupPlaybackTarget("b", settings.key)), played)
        assertEquals(setOf("b"), channels.requested); assertEquals(1, channels.lookupReads)
        assertEquals(0, channels.allReads)
        assertEquals(listOf("b", "a"), history.records(settings.key).value.map { it.channelId })
    }

    @Test fun returningHomeAndRecomposingCannotReplayTheStartupIntent() = runTest {
        seed("b", "a")
        val controller = controller(); ready(controller); ready(controller); runCurrent()
        repeat(5) { ready(controller); runCurrent() }
        assertEquals(1, played.size); assertEquals(1, channels.lookupReads)
    }

    @Test fun restoringAnActivityNeverStartsPlaybackEvenWithEnabledPreference() = runTest {
        seed("a")
        val restored = controller(); restored.initialize(false); ready(restored); runCurrent()
        assertTrue(played.isEmpty()); assertEquals(0, preference.reads)
        val savedAttempt = controller(restored = true); ready(savedAttempt); runCurrent()
        assertTrue(played.isEmpty()); assertEquals(0, channels.lookupReads)
    }

    @Test fun actualInputBeforeHomeIsReadyPermanentlyCancelsThisLaunch() = runTest {
        seed("a")
        val controller = controller(); controller.cancel(); ready(controller); runCurrent()
        assertTrue(persisted); assertTrue(played.isEmpty()); assertEquals(0, preference.reads)
    }

    @Test fun pausingOrLeavingHomeDuringLookupCannotResumeOnReturn() = runTest {
        seed("a")
        val response = CompletableDeferred<List<Channel>>()
        channels.customLookup = { _, _ -> response.await() }
        val controller = controller(); ready(controller); runCurrent()
        controller.cancel(); ready(controller); response.complete(channels.rows); runCurrent()
        assertTrue(played.isEmpty()); assertEquals(1, channels.lookupReads)
    }

    @Test fun evenAnUncooperativeCancelledLookupCannotNavigateAfterInput() = runTest {
        seed("a")
        val response = CompletableDeferred<List<Channel>>()
        channels.customLookup = { _, _ -> withContext(NonCancellable) { response.await() } }
        val controller = controller(); ready(controller); runCurrent()
        controller.cancel(); response.complete(channels.rows); runCurrent()
        assertTrue(played.isEmpty())
    }

    @Test fun emptyHistoryAndAnUnconfiguredSourceStayAtHomeWithoutChannelReads() = runTest {
        val controller = controller(); ready(controller); runCurrent()
        assertTrue(played.isEmpty()); assertEquals(0, channels.lookupReads)
        settings.value.value = AppSettings()
        val emptySource = controller(); ready(emptySource); runCurrent()
        assertTrue(played.isEmpty()); assertEquals(0, channels.lookupReads)
    }

    @Test fun aDeletedLatestChannelStaysAtHomeRatherThanPlayingAnOlderOne() = runTest {
        seed("deleted", "a")
        val controller = controller(); ready(controller); runCurrent()
        assertTrue(played.isEmpty()); assertEquals(setOf("deleted"), channels.requested)
        assertEquals(1, channels.lookupReads); assertEquals(0, channels.allReads)
    }

    @Test fun emptyCurrentStreamUrlCannotBeResumed() = runTest {
        seed("a"); channels.rows = channels.rows.map { it.copy(streamUrl = "") }
        val controller = controller(); ready(controller); runCurrent()
        assertTrue(played.isEmpty())
    }

    @Test fun anotherSourcesHistoryIsNeverUsed() = runTest {
        seed("a")
        val previous = settings.key
        settings.value.value = settings.value.value.copy(m3uUrl = "https://unit.test/other")
        val controller = controller(); ready(controller); runCurrent()
        assertTrue(played.isEmpty()); assertEquals(0, channels.lookupReads)
        val oldHome = controller(); ready(oldHome, previous); runCurrent()
        assertTrue(played.isEmpty()); assertEquals(0, channels.lookupReads)
    }

    @Test fun changingAccountDuringLookupRejectsTheOldSourceTarget() = runTest {
        seed("a")
        val response = CompletableDeferred<List<Channel>>()
        channels.customLookup = { _, _ -> response.await() }
        val controller = controller(); ready(controller); runCurrent()
        settings.value.value = settings.value.value.copy(m3uUrl = "https://unit.test/other")
        response.complete(channels.rows); runCurrent()
        assertTrue(played.isEmpty())
    }

    @Test fun changingXtreamPasswordAlsoChangesTheResumeScope() = runTest {
        settings.value.value = AppSettings(source = PlaylistSource.XTREAM, xtreamServer = "https://unit.test",
            xtreamUsername = "user", xtreamPassword = "first")
        seed("a")
        val response = CompletableDeferred<List<Channel>>()
        channels.customLookup = { _, _ -> response.await() }
        val controller = controller(); ready(controller); runCurrent()
        settings.value.value = settings.value.value.copy(xtreamPassword = "second")
        response.complete(channels.rows); runCurrent()
        assertTrue(played.isEmpty())
    }

    @Test fun clearingHistoryOrDisablingThePreferenceDuringLookupPreventsNavigation() = runTest {
        for (clearHistory in listOf(true, false)) {
            seed("a"); preference.value.value = true
            val response = CompletableDeferred<List<Channel>>()
            channels.customLookup = { _, _ -> response.await() }
            val controller = controller(); ready(controller); runCurrent()
            if (clearHistory) history.clear(settings.key) else preference.value.value = false
            response.complete(channels.rows); runCurrent()
            assertTrue(played.isEmpty())
        }
    }

    @Test fun optionalReadAndLookupFailuresLeaveHomeUsableWithoutRetryingAutomatically() = runTest {
        seed("a")
        preference.failRead = true
        val first = controller(); ready(first); runCurrent(); ready(first)
        assertTrue(played.isEmpty()); assertEquals(0, channels.lookupReads)
        preference.failRead = false; history.failRead = true
        val second = controller(); ready(second); runCurrent()
        assertTrue(played.isEmpty()); assertEquals(0, channels.lookupReads)
        history.failRead = false; channels.fail = true
        val third = controller(); ready(third); runCurrent(); ready(third); runCurrent()
        assertTrue(played.isEmpty()); assertEquals(1, channels.lookupReads)
    }

    @Test fun slowOptionalReadIsBoundedAndDoesNotStartALatePlayback() = runTest {
        seed("a"); preference.readGate = CompletableDeferred()
        val controller = controller(); ready(controller); runCurrent()
        advanceTimeBy(3_001); runCurrent()
        preference.readGate!!.complete(Unit); runCurrent(); ready(controller); runCurrent()
        assertTrue(played.isEmpty()); assertEquals(0, channels.lookupReads)
    }

    @Test fun switchingTheLatestHistoryEntryDuringLookupDoesNotOpenAStaleChannel() = runTest {
        seed("a")
        val response = CompletableDeferred<List<Channel>>()
        channels.customLookup = { _, _ -> response.await() }
        val controller = controller(); ready(controller); runCurrent()
        seed("b", "a"); response.complete(channels.rows); runCurrent()
        assertTrue(played.isEmpty())
    }
}