package com.yourapp.iptv.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.OrganizationRepository
import com.yourapp.iptv.ui.state.LastChannelError
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerLastChannelIntegrationTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    private val settings = HistorySettings()
    private val history = RecallHistory()
    private val channels = HistoryChannels()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { owner.clear(); Dispatchers.resetMain() }

    @Test fun switchingTheConfirmedPairKeepsTheQueueAndDoesNotImportOrReloadChannels() = runTest(dispatcher) {
        val model = model(); runCurrent()
        assertEquals(0, history.subscriptions)
        model.onPlaybackStarted(channels.rows[0])
        model.selectChannel("b"); model.onPlaybackStarted(channels.rows[1])
        model.showLastChannelMenu(); runCurrent()
        val first = model.recallLastChannel()!!
        assertEquals("a", first.channel.id)
        assertTrue(model.selectHistoryChannel(first.channel, first.sourceKey))
        assertEquals("a", model.uiState.value.channel?.id)
        assertEquals("One", model.uiState.value.selectedGroup)
        model.onPlaybackStarted(first.channel); runCurrent()
        val second = model.recallLastChannel()!!
        assertEquals("b", second.channel.id)
        assertTrue(model.selectHistoryChannel(second.channel, second.sourceKey))
        assertEquals("b", model.uiState.value.channel?.id)
        assertEquals(2, channels.lookupReads); assertEquals(1, channels.allReads)
        assertEquals(listOf("a", "b"), history.memory.records(settings.key).value.map { it.channelId })
    }

    @Test fun focusingAndAnUnconfirmedFailedChannelCannotReplaceTheConfirmedPair() = runTest(dispatcher) {
        val model = model(group = null); runCurrent()
        model.onPlaybackStarted(channels.rows[0])
        model.selectChannel("b"); model.onPlaybackStarted(channels.rows[1])
        model.selectChannel("c")
        model.onPlaybackStarted(channels.rows[0]) // Delayed frame from an old selection.
        model.showLastChannelMenu(); runCurrent()
        val result = model.recallLastChannel()!!
        assertEquals("b", result.channel.id)
        assertTrue(model.selectHistoryChannel(result.channel, result.sourceKey))
        model.onPlaybackStarted(result.channel); runCurrent()
        assertEquals("a", model.recallLastChannel()?.channel?.id)
        assertEquals(listOf("b", "a"), history.memory.records(settings.key).value.map { it.channelId })
        assertEquals(1, channels.allReads)
    }

    @Test fun reopeningAPlayerUsesTheExistingSourceHistoryWithoutCreatingASecondSubscription() = runTest(dispatcher) {
        val first = model(); runCurrent()
        first.onPlaybackStarted(channels.rows[0])
        first.selectChannel("b"); first.onPlaybackStarted(channels.rows[1])
        first.showLastChannelMenu(); runCurrent(); assertEquals(1, history.active)
        owner.clear(); runCurrent(); assertEquals(0, history.active)
        val reopened = model(id = "b"); runCurrent()
        assertEquals(0, history.active)
        reopened.showLastChannelMenu(); reopened.showLastChannelMenu(); runCurrent()
        assertEquals(1, history.active); assertEquals("a", reopened.recallLastChannel()?.channel?.id)
    }

    @Test fun historyRemainsIndependentWhenTwoAccountsHaveTheSameChannelIds() = runTest(dispatcher) {
        val firstKey = settings.key
        history.set(firstKey, "b", "a")
        val first = model(id = "b"); runCurrent(); first.showLastChannelMenu(); runCurrent()
        assertEquals("a", first.recallLastChannel()?.channel?.id)
        owner.clear(); runCurrent()
        settings.value.value = settings.value.value.copy(m3uUrl = "https://unit.test/second-source")
        val second = model(id = "b"); runCurrent(); second.showLastChannelMenu(); runCurrent()
        assertFalse(second.lastChannelState.value.canRecall); assertNull(second.recallLastChannel())
        second.onPlaybackStarted(channels.rows[1]); runCurrent()
        assertFalse(second.lastChannelState.value.canRecall)
        assertEquals(listOf("b", "a"), history.memory.records(firstKey).value.map { it.channelId })
        assertEquals(listOf("b"), history.memory.records(settings.key).value.map { it.channelId })
    }

    @Test fun recallingAChannelOutsideTheCurrentGroupUsesTheLoadedFullQueue() = runTest(dispatcher) {
        history.set(settings.key, "a", "c")
        val model = model(); runCurrent(); model.showLastChannelMenu(); runCurrent()
        val result = model.recallLastChannel()!!
        assertEquals("c", result.channel.id)
        assertTrue(model.selectHistoryChannel(result.channel, result.sourceKey)); runCurrent()
        assertEquals("c", model.uiState.value.channel?.id)
        assertNull(model.uiState.value.selectedGroup)
        assertEquals(3, model.uiState.value.queue.size); assertEquals(1, channels.allReads)
    }

    @Test fun sourceChangesAndDeletedChannelsShowMenuErrorsWithoutInterruptingThePlayingChannel() = runTest(dispatcher) {
        history.set(settings.key, "a", "gone")
        val model = model(); runCurrent(); model.showLastChannelMenu(); runCurrent()
        assertNull(model.recallLastChannel())
        assertEquals(LastChannelError.UNAVAILABLE, model.lastChannelState.value.error)
        assertEquals("a", model.uiState.value.channel?.id); assertNull(model.uiState.value.errorMessage)
        history.set(settings.key, "a", "b"); runCurrent()
        settings.value.value = settings.value.value.copy(m3uUrl = "https://unit.test/other-source")
        assertNull(model.recallLastChannel())
        assertEquals(LastChannelError.SOURCE_CHANGED, model.lastChannelState.value.error)
        assertEquals("a", model.uiState.value.channel?.id); assertNull(model.uiState.value.errorMessage)
    }

    private fun model(id: String = "a", group: String? = "One") = PlayerViewModel(
        SavedStateHandle(mapOf("channelId" to id, "group" to group)), channels, dispatcher,
        FakeFavoriteRepository(), OrganizationRepository.Disabled, history, settings).also { owner.put("player", it) }
}