package com.yourapp.iptv.data.datastore

import com.yourapp.iptv.data.profile.*
import com.yourapp.iptv.domain.diagnostics.DiagnosticJournal
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class WatchHistoryPressureTest {
    @After fun cleanup() { DiagnosticJournal.clear() }
    @Test fun blockedDiskBurstAndExplicitBarriersMatchTheOriginalHistoryContract() = runTest {
        val settings = HistoryPressureSettings();val key = PlaylistCacheKey.of(settings.value.value)
        val disk = HistoryPressureStore().apply { gate = CompletableDeferred() }
        val repository = WatchHistoryDataStore(disk,settings,backgroundScope)
        var expected = emptyList<WatchHistoryEntry>()
        fun record(id: String,time: Long = 0) {
            val e = WatchHistoryEntry(id,time);repository.recordStarted(key,e);expected = WatchHistory.record(expected,e)
        }
        record("seed",500_000);runCurrent()
        repeat(100_000) { record("id-${it % 40}") }
        val deleting = async(start = CoroutineStart.UNDISPATCHED) { repository.remove(key,"id-39") }
        expected = expected.filterNot { it.channelId == "id-39" }
        record("after-remove")
        val clearing = async(start = CoroutineStart.UNDISPATCHED) { repository.clear(key) }
        expected = emptyList()
        repeat(100) { record("after-clear-$it",1) }
        assertFalse(deleting.isCompleted);assertFalse(clearing.isCompleted);assertEquals(0,disk.writes)
        disk.gate!!.complete(Unit);runCurrent();deleting.await();clearing.await()
        assertEquals(expected,repository.observe(key).first())
        // One executing run + three compacted runs + two edits, independent of the event count.
        assertEquals(6,disk.writes)
    }
    @Test fun aBusyWriterReportsEditFailureAndStillKeepsAllAcceptedEditsInOrder() = runTest {
        val settings = HistoryPressureSettings();val key = PlaylistCacheKey.of(settings.value.value)
        val disk = HistoryPressureStore().apply { gate = CompletableDeferred() }
        val repository = WatchHistoryDataStore(disk,settings,backgroundScope)
        repository.recordStarted(key,WatchHistoryEntry("old",1));runCurrent()
        val accepted = List(WatchHistoryWriter.MAX_EDITS) { async(start = CoroutineStart.UNDISPATCHED) { repository.clear(key) } }
        assertTrue(runCatching { repository.remove(key,"overflow") }.isFailure)
        repository.recordStarted(key,WatchHistoryEntry("new",2))
        disk.gate!!.complete(Unit);runCurrent();accepted.forEach { it.await() }
        assertEquals(listOf(WatchHistoryEntry("new",2)),repository.observe(key).first())
    }
    @Test fun aFailedRecordRunDoesNotKillTheWriterOrReplayOldEventsAcrossClear() = runTest {
        val settings = HistoryPressureSettings();val key = PlaylistCacheKey.of(settings.value.value)
        val disk = HistoryPressureStore().apply { fail = true }
        val repository = WatchHistoryDataStore(disk,settings,backgroundScope)
        repeat(1000) { repository.recordStarted(key,WatchHistoryEntry("old-$it",it.toLong())) };runCurrent()
        assertTrue(repository.observe(key).first().isEmpty())
        disk.fail = false
        val clear = async(start = CoroutineStart.UNDISPATCHED) { repository.clear(key) }
        repository.recordStarted(key,WatchHistoryEntry("fresh",1));runCurrent();clear.await()
        assertEquals(listOf(WatchHistoryEntry("fresh",1)),repository.observe(key).first())
    }
    @Test fun compactedWritesRemainInTheirCapturedProfilesAfterSwitching() = runTest {
        val settings = HistoryPressureSettings();val disk = HistoryPressureStore().apply { gate = CompletableDeferred() }
        val people = ProfilePeople(UserProfiles(UserProfiles().entries + profileRow() + profileRow(PROFILE_B),PROFILE_A))
        val repository = WatchHistoryDataStore(disk,settings,backgroundScope,people)
        val raw = PlaylistCacheKey.of(settings.value.value);val a = ProfileScope.key(PROFILE_A,raw);val b = ProfileScope.key(PROFILE_B,raw)
        repository.recordStarted(a,WatchHistoryEntry("a-first",1));runCurrent()
        repeat(1000) { repository.recordStarted(a,WatchHistoryEntry("a-$it",it.toLong())) }
        people.state.value = people.state.value.copy(catalog = people.state.value.catalog.copy(activeId = PROFILE_B))
        repeat(1000) { repository.recordStarted(b,WatchHistoryEntry("b-$it",it.toLong())) }
        disk.gate!!.complete(Unit);runCurrent()
        assertEquals((999 downTo 980).map { "a-$it" },repository.observe(a).first().map { it.channelId })
        assertEquals((999 downTo 980).map { "b-$it" },repository.observe(b).first().map { it.channelId })
    }
    @Test fun removingAProfileDuringBlockedStorageCannotResurrectItsHistory() = runTest {
        val settings = HistoryPressureSettings();val disk = HistoryPressureStore().apply { gate = CompletableDeferred() }
        val people = ProfilePeople(UserProfiles(UserProfiles().entries + profileRow(),PROFILE_A))
        val repository = WatchHistoryDataStore(disk,settings,backgroundScope,people)
        val raw = PlaylistCacheKey.of(settings.value.value);val a = ProfileScope.key(PROFILE_A,raw)
        repository.recordStarted(a,WatchHistoryEntry("private",1));runCurrent()
        repeat(1000) { repository.recordStarted(a,WatchHistoryEntry("private-$it",it.toLong())) }
        people.state.value = people.state.value.copy(catalog = UserProfiles())
        repository.recordStarted(raw,WatchHistoryEntry("default",1))
        disk.gate!!.complete(Unit);runCurrent()
        assertTrue(repository.observe(a).first().isEmpty())
        assertEquals("default",repository.observe(raw).first().single().channelId)
    }
    @Test fun stoppingTheWriterDuringBlockedIoDoesNotLeaveExplicitEditsWaiting() = runTest {
        val settings = HistoryPressureSettings();val key = PlaylistCacheKey.of(settings.value.value)
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val disk = HistoryPressureStore().apply { gate = CompletableDeferred() }
        val repository = WatchHistoryDataStore(disk,settings,scope)
        repository.recordStarted(key,WatchHistoryEntry("old",1));runCurrent()
        val clear = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repository.clear(key) } }
        scope.cancel();runCurrent()
        withTimeout(1000) { assertTrue(clear.await().isFailure) }
        assertEquals(0,disk.writes)
    }
}