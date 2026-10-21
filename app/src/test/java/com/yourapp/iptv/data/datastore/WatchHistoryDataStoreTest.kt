package com.yourapp.iptv.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class WatchHistoryDataStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val settings = Settings()
    private val key get() = PlaylistCacheKey.of(settings.value.value)

    @Test fun pendingRecordsClearAndNewRecordsCommitInSubmissionOrder() = runTest {
        val disk = MemoryStore()
        val repository = WatchHistoryDataStore(disk, settings, backgroundScope)
        repository.recordStarted(key, WatchHistoryEntry("a", 1))
        repository.recordStarted(key, WatchHistoryEntry("b", 2))
        val clearing = launch(start = CoroutineStart.UNDISPATCHED) { repository.clear(key) }
        repository.recordStarted(key, WatchHistoryEntry("c", 3))
        runCurrent(); clearing.join()
        assertEquals(listOf("c"), repository.observe(key).first().map { it.channelId })
    }

    @Test fun aBurstKeepsTwentyUniqueChannelsAndRemovalCannotBeUndoneByOlderQueuedRecords() = runTest {
        val repository = WatchHistoryDataStore(MemoryStore(), settings, backgroundScope)
        repeat(100) { repository.recordStarted(key, WatchHistoryEntry("id-$it", it.toLong())) }
        val deleting = launch(start = CoroutineStart.UNDISPATCHED) { repository.remove(key, "id-99") }
        runCurrent(); deleting.join()
        val rows = repository.observe(key).first()
        assertEquals((98 downTo 80).map { "id-$it" }, rows.map { it.channelId })
    }

    @Test fun sourceChangesRejectOldQueuedEventsAndNeverClearTheOtherAccount() = runTest {
        val repository = WatchHistoryDataStore(MemoryStore(), settings, backgroundScope)
        val first = key
        repository.recordStarted(first, WatchHistoryEntry("old", 1))
        settings.value.value = AppSettings(m3uUrl = "https://unit.test/other")
        val second = key
        repository.recordStarted(second, WatchHistoryEntry("new", 2))
        runCurrent()
        assertTrue(repository.observe(first).first().isEmpty())
        assertEquals("new", repository.observe(second).first().single().channelId)
        val clearing = async { runCatching { repository.clear(first) } }
        runCurrent(); assertTrue(clearing.await().isFailure)
        assertEquals("new", repository.observe(second).first().single().channelId)
    }

    @Test fun aFailedTransactionDoesNotKillTheWriterAndTheNextExplicitEditCanSucceed() = runTest {
        val disk = MemoryStore()
        val repository = WatchHistoryDataStore(disk, settings, backgroundScope)
        repository.recordStarted(key, WatchHistoryEntry("a", 1)); runCurrent()
        disk.fail = true
        val deleting = async { runCatching { repository.remove(key, "a") } }
        runCurrent(); assertTrue(deleting.await().isFailure)
        assertEquals("a", repository.observe(key).first().single().channelId)
        disk.fail = false
        val retry = launch { repository.remove(key, "a") }
        runCurrent(); retry.join(); assertTrue(repository.observe(key).first().isEmpty())
    }

    @Test fun acceptedEditsSurviveCancellationOfTheirCallingScreen() = runTest {
        val disk = MemoryStore()
        val repository = WatchHistoryDataStore(disk, settings, backgroundScope)
        repository.recordStarted(key, WatchHistoryEntry("a", 1)); runCurrent()
        disk.gate = CompletableDeferred()
        val caller = launch(start = CoroutineStart.UNDISPATCHED) { repository.remove(key, "a") }
        runCurrent(); caller.cancel()
        disk.gate!!.complete(Unit); runCurrent()
        assertTrue(repository.observe(key).first().isEmpty())
    }

    @Test fun malformedRecordsAreLocalToTheirSourceAndUnrelatedPreferencesAreKept() = runTest {
        val disk = MemoryStore(mutablePreferencesOf(stringPreferencesKey("history_$key") to "broken",
            stringPreferencesKey("future") to "preserve"))
        val repository = WatchHistoryDataStore(disk, settings, backgroundScope)
        assertTrue(repository.observe(key).first().isEmpty())
        repository.recordStarted(key, WatchHistoryEntry("a", 1)); runCurrent()
        assertEquals("a", repository.observe(key).first().single().channelId)
        assertEquals("preserve", disk.data.first()[stringPreferencesKey("future")])
    }

    @Test fun recordsSurviveActualFileReopeningWithoutSavingStreamMetadata() = runBlocking {
        val file = temporary.root.resolve("history.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val disk = PreferenceDataStoreFactory.create(scope = scope) { file }
            val repository = WatchHistoryDataStore(disk, settings, scope)
            repository.recordStarted(key, WatchHistoryEntry("stable-id", 100))
            withTimeout(5000) { repository.observe(key).first { it.isNotEmpty() } }
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = WatchHistoryDataStore(PreferenceDataStoreFactory.create(scope = scope) { file }, settings, scope)
            assertEquals(listOf(WatchHistoryEntry("stable-id", 100)), repository.observe(key).first())
            assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("https://"))
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun sourceHistoryRetentionIsBounded() = runTest {
        val disk = MemoryStore()
        val repository = WatchHistoryDataStore(disk, settings, backgroundScope)
        repeat(40) {
            settings.value.value = AppSettings(m3uUrl = "https://unit.test/list-$it")
            repository.recordStarted(key, WatchHistoryEntry("a", it.toLong())); runCurrent()
        }
        assertEquals(32, disk.data.first().asMap().keys.count { it.name.startsWith("history_") })
        assertEquals("a", repository.observe(key).first().single().channelId)
    }

    private class Settings : SettingsRepository {
        val value = MutableStateFlow(AppSettings(m3uUrl = "https://unit.test/list"))
        override fun observeSettings() = value
        override suspend fun saveSettings(settings: AppSettings) { value.value = settings }
    }
    private class MemoryStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val values = MutableStateFlow(initial)
        private val mutex = Mutex()
        override val data: Flow<Preferences> = values
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            gate?.await()
            if (fail) throw IOException("Unavailable history write")
            transform(values.value).also { values.value = it }
        }
    }
}