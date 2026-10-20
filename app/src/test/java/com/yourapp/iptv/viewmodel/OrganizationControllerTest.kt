package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.domain.repository.OrganizationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrganizationControllerTest {
    @Test fun refreshWithTheSameSourcePublishesCachedChannelsSoLoadingCanFinish() = runTest {
        val repository = object : OrganizationRepository {
            override fun observe() = flowOf(OrganizationConfig(enabled = true))
            override suspend fun save(value: OrganizationConfig) = Unit
        }
        val received = mutableListOf<OrganizedChannels>()
        val controller = OrganizationController(backgroundScope, repository, StandardTestDispatcher(testScheduler), received::add)
        val source = OrganizationChannelsFixture().rows
        controller.setChannels(source); runCurrent()
        assertEquals(1, received.size)
        controller.clearChannels(); controller.setChannels(source); runCurrent()
        assertEquals(2, received.size)
        assertSame(received[0].channels, received[1].channels)
    }
    @Test fun readRetriesKeepChannelsAndSignalOnlyFailureAndRecoveryChanges() = runTest {
        val repository = OrganizationMemory().apply { failRead = true }
        val received = mutableListOf<OrganizedChannels>()
        val controller = OrganizationController(backgroundScope, repository, StandardTestDispatcher(testScheduler), received::add)
        controller.setChannels(OrganizationChannelsFixture().rows); runCurrent()
        assertEquals(1, received.size); assertTrue(received.single().tree.readFailed)
        advanceTimeBy(5000); runCurrent(); assertEquals(1, received.size)
        repository.failRead = false
        advanceTimeBy(5000); runCurrent()
        assertEquals(2, received.size); assertFalse(received.last().tree.readFailed)
        assertTrue(received.last().tree.enabled); assertEquals(0, repository.writes)
    }
}