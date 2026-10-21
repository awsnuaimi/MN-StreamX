package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.state.LastChannelError
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LastChannelControllerTest {
    private val settings = HistorySettings()
    private val history = RecallHistory()
    private val channels = HistoryChannels()
    private var selected = channels.rows[1]

    private fun TestScope.controller() = LastChannelController(backgroundScope, history, channels, settings) { selected }
        .also { it.bind(settings.key) }

    @Test fun historyIsObservedOnlyWhileMoreIsOpenAndReopeningReadsTheCurrentRecords() = runTest {
        history.set(settings.key, "b", "a")
        val controller = controller(); runCurrent()
        assertEquals(0, history.subscriptions); assertEquals(0, channels.lookupReads)
        controller.start(); runCurrent()
        assertEquals(1, history.active); assertEquals("a", controller.uiState.value.targetId)
        assertEquals(0, channels.lookupReads)
        controller.stop(); runCurrent()
        assertEquals(0, history.active); assertFalse(controller.uiState.value.canRecall)
        history.set(settings.key, "c", "b")
        controller.start(); runCurrent()
        assertEquals(1, history.active); assertEquals("c", controller.uiState.value.targetId)
        assertEquals(0, channels.allReads)
    }

    @Test fun recallAlternatesConfirmedChannelsWithoutRecordingTheSelectionItself() = runTest {
        history.set(settings.key, "b", "a")
        val controller = controller(); controller.start(); runCurrent()
        val first = controller.recall()!!
        assertEquals("a", first.channel.id); assertEquals(settings.key, first.sourceKey)
        selected = first.channel; controller.onSelectionChanged()
        assertEquals("b", controller.recall()?.channel?.id)
        assertEquals(listOf("b", "a"), history.memory.records(settings.key).value.map { it.channelId })
        assertEquals(2, channels.lookupReads); assertEquals(0, channels.allReads)
    }

    @Test fun aFailedUnconfirmedSelectionReturnsToTheMostRecentlyConfirmedChannel() = runTest {
        history.set(settings.key, "b", "a"); selected = channels.rows[2]
        val controller = controller(); controller.start(); runCurrent()
        assertEquals("b", controller.recall()?.channel?.id)
        assertEquals(listOf("b", "a"), history.memory.records(settings.key).value.map { it.channelId })
    }

    @Test fun aSingleConfirmedActiveChannelKeepsRecallDisabledWithoutReadingChannelData() = runTest {
        history.set(settings.key, "b")
        val controller = controller(); controller.start(); runCurrent()
        assertFalse(controller.uiState.value.canRecall); assertNull(controller.recall())
        assertEquals(0, channels.lookupReads)
    }

    @Test fun deletedChannelsAreSkippedInOneBoundedBatchUsingCurrentMetadata() = runTest {
        history.set(settings.key, "b", "gone", "a")
        channels.rows = channels.rows.map { if (it.id == "a") it.copy(streamUrl = "https://unit.test/new-a") else it }
        val controller = controller(); controller.start(); runCurrent()
        val result = controller.recall()!!
        assertEquals("a", result.channel.id); assertEquals("https://unit.test/new-a", result.channel.streamUrl)
        assertEquals(setOf("gone", "a"), channels.requested)
        assertEquals(1, channels.lookupReads); assertEquals(0, channels.allReads)
    }

    @Test fun missingHistoryChannelsDoNotReplaceThePlayingChannel() = runTest {
        history.set(settings.key, "b", "gone")
        val controller = controller(); controller.start(); runCurrent()
        assertNull(controller.recall()); assertEquals("b", selected.id)
        assertEquals(LastChannelError.UNAVAILABLE, controller.uiState.value.error)
        assertFalse(controller.uiState.value.canRecall)
    }

    @Test fun changingAccountBeforeRecallPreventsEveryChannelLookup() = runTest {
        history.set(settings.key, "b", "a")
        val controller = controller(); controller.start(); runCurrent()
        settings.value.value = settings.value.value.copy(m3uUrl = "https://unit.test/other")
        assertNull(controller.recall()); assertEquals(0, channels.lookupReads)
        assertEquals(LastChannelError.SOURCE_CHANGED, controller.uiState.value.error)
        assertEquals("b", selected.id)
    }

    @Test fun changingAccountDuringLookupCannotPublishAnOldSourceTarget() = runTest {
        history.set(settings.key, "b", "a")
        val response = CompletableDeferred<List<Channel>>()
        channels.customLookup = { _, _ -> response.await() }
        val controller = controller(); controller.start(); runCurrent()
        val pending = async { controller.recall() }; runCurrent()
        settings.value.value = settings.value.value.copy(m3uUrl = "https://unit.test/other")
        response.complete(channels.rows); runCurrent()
        assertNull(pending.await())
        assertEquals(LastChannelError.SOURCE_CHANGED, controller.uiState.value.error)
    }

    @Test fun aManualChannelChangeDuringLookupCancelsTheRecallIntent() = runTest {
        history.set(settings.key, "b", "a")
        val response = CompletableDeferred<List<Channel>>()
        channels.customLookup = { _, _ -> response.await() }
        val controller = controller(); controller.start(); runCurrent()
        val pending = async { controller.recall() }; runCurrent()
        selected = channels.rows[2]; controller.onSelectionChanged()
        response.complete(channels.rows); runCurrent()
        assertNull(pending.await()); assertEquals("c", selected.id)
        assertEquals("b", controller.uiState.value.targetId)
    }

    @Test fun stoppingAndReopeningTheMenuRejectsALateLookupWithoutOverwritingNewState() = runTest {
        history.set(settings.key, "b", "a")
        val response = CompletableDeferred<List<Channel>>()
        channels.customLookup = { _, _ -> withContext(NonCancellable) { response.await() } }
        val controller = controller(); controller.start(); runCurrent()
        val pending = async { controller.recall() }; runCurrent()
        controller.stop(); history.set(settings.key, "b", "c"); controller.start(); runCurrent()
        response.complete(channels.rows); runCurrent()
        assertNull(pending.await()); assertEquals("c", controller.uiState.value.targetId)
        assertNull(controller.uiState.value.error); assertFalse(controller.uiState.value.isSwitching)
    }

    @Test fun cancellingTheUiCoroutinePreventsEvenAnUncooperativeLookupFromReturningATarget() = runTest {
        history.set(settings.key, "b", "a")
        val response = CompletableDeferred<List<Channel>>()
        channels.customLookup = { _, _ -> withContext(NonCancellable) { response.await() } }
        val controller = controller(); controller.start(); runCurrent()
        val pending = async { controller.recall() }; runCurrent()
        pending.cancel(); response.complete(channels.rows); runCurrent()
        assertTrue(pending.isCancelled); assertFalse(controller.uiState.value.isSwitching)
    }

    @Test fun clearingHistoryDuringLookupPreventsReturningARemovedRecord() = runTest {
        history.set(settings.key, "b", "a")
        val response = CompletableDeferred<List<Channel>>()
        channels.customLookup = { _, _ -> response.await() }
        val controller = controller(); controller.start(); runCurrent()
        val pending = async { controller.recall() }; runCurrent()
        history.clear(settings.key); runCurrent()
        response.complete(channels.rows); runCurrent()
        assertNull(pending.await()); assertFalse(controller.uiState.value.canRecall)
    }

    @Test fun historyReadErrorsCanBeRetriedWithoutAnyPlaybackReload() = runTest {
        history.set(settings.key, "b", "a"); history.memory.failRead = true
        val controller = controller(); controller.start(); runCurrent()
        assertEquals(LastChannelError.HISTORY, controller.uiState.value.error)
        history.memory.failRead = false; controller.retry(); runCurrent()
        assertEquals("a", controller.uiState.value.targetId); assertNull(controller.uiState.value.error)
        assertEquals(0, channels.lookupReads); assertEquals(0, channels.allReads)
    }

    @Test fun lookupFailureCanBeRetriedAndRepeatedClicksNeverLaunchParallelRequests() = runTest {
        history.set(settings.key, "b", "a")
        val controller = controller(); controller.start(); runCurrent()
        channels.fail = true
        assertNull(controller.recall()); assertEquals(LastChannelError.LOOKUP, controller.uiState.value.error)
        channels.fail = false
        val response = CompletableDeferred<List<Channel>>()
        channels.customLookup = { _, _ -> response.await() }
        val pending = async { controller.recall() }; runCurrent()
        assertTrue(controller.uiState.value.isSwitching); assertNull(controller.recall())
        assertEquals(2, channels.lookupReads)
        response.complete(channels.rows); runCurrent()
        assertEquals("a", pending.await()?.channel?.id); assertNull(controller.uiState.value.error)
    }
}