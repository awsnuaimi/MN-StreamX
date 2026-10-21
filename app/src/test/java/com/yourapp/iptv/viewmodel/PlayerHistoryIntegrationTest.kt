package com.yourapp.iptv.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.OrganizationRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerHistoryIntegrationTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    private val settings = HistorySettings()
    private val history = HistoryMemory()
    private val channels = HistoryChannels()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { owner.clear(); Dispatchers.resetMain() }

    @Test fun changingOrFocusingChannelsDoesNotRecordAndOldFramesCannotChangeHistory() = runTest(dispatcher) {
        val model = model(); runCurrent()
        assertTrue(history.records(settings.key).value.isEmpty())
        model.onPlaybackStarted(channels.rows[0])
        model.selectChannel("b"); runCurrent()
        model.onPlaybackStarted(channels.rows[0])
        assertEquals(listOf("a"), history.records(settings.key).value.map { it.channelId })
        model.onPlaybackStarted(channels.rows[1])
        assertEquals(listOf("b", "a"), history.records(settings.key).value.map { it.channelId })
        assertEquals(1, channels.allReads)
    }
    @Test fun openingAHistoryChannelOutsideTheCurrentGroupReusesTheLoadedQueue() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("channelId" to "a", "group" to "One"))
        val model = model(saved); runCurrent()
        assertEquals(2, model.uiState.value.queue.size)
        assertTrue(model.selectHistoryChannel(channels.rows[2], settings.key)); runCurrent()
        assertEquals("c", model.uiState.value.channel?.id)
        assertNull(model.uiState.value.selectedGroup)
        assertEquals(3, model.uiState.value.queue.size)
        assertEquals(1, channels.allReads)
        assertTrue(history.records(settings.key).value.isEmpty())
        assertFalse(model.selectHistoryChannel(channels.rows[0], "wrong-account"))
        assertFalse(model.selectHistoryChannel(channels.rows[0].copy(streamUrl = "https://unit.test/changed"), settings.key))
    }
    @Test fun anExplicitHistoryRouteCannotOpenAgainstADifferentSource() = runTest(dispatcher) {
        val model = model(SavedStateHandle(mapOf("channelId" to "a", "sourceKey" to "different-source")))
        runCurrent()
        assertNull(model.uiState.value.channel); assertTrue(model.uiState.value.sourceChanged)
        assertFalse(model.uiState.value.isLoading); assertEquals(0, channels.allReads)
        assertTrue(history.records(settings.key).value.isEmpty())
    }
    @Test fun aHistoryChannelAlreadyInTheQueueKeepsTheCurrentGroupAndStateChangesImmediately() = runTest(dispatcher) {
        val model = model(SavedStateHandle(mapOf("channelId" to "a", "group" to "One"))); runCurrent()
        assertTrue(model.selectHistoryChannel(channels.rows[1], settings.key))
        assertEquals("b", model.uiState.value.channel?.id)
        assertEquals("One", model.uiState.value.selectedGroup)
        assertEquals(1, channels.allReads)
    }
    private fun model(saved: SavedStateHandle = SavedStateHandle(mapOf("channelId" to "a"))) =
        PlayerViewModel(saved, channels, dispatcher, FakeFavoriteRepository(), OrganizationRepository.Disabled,
            history, settings).also { owner.put("player", it) }
}