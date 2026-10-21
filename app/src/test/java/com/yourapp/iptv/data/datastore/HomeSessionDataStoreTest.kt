package com.yourapp.iptv.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.yourapp.iptv.domain.model.*
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
class HomeSessionDataStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val a = "a".repeat(64)
    private val b = "b".repeat(64)

    @Test fun manyFocusEventsProduceOneLatestWriteEvenBeforeTheCollectorStarts() = runTest {
        val disk = MemoryStore()
        val repository = HomeSessionDataStore(disk, backgroundScope)
        repeat(100) { repository.remember(a, session(it)) }
        runCurrent(); advanceTimeBy(349); runCurrent()
        assertEquals(0, disk.writes)
        advanceTimeBy(2); runCurrent()
        assertEquals(1, disk.writes)
        assertEquals(session(99), HomeSessionDataStore(disk, backgroundScope).read(a))
        repository.remember(a, session(99)); advanceTimeBy(500); runCurrent()
        assertEquals(1, disk.writes)
    }

    @Test fun flushCommitsWithoutWaitingForDebounceAndSourcesStaySeparate() = runTest {
        val disk = MemoryStore()
        val repository = HomeSessionDataStore(disk, backgroundScope)
        repository.remember(a, session(1)); repository.remember(b, session(2)); repository.flush()
        runCurrent()
        assertEquals(1, disk.writes)
        val reopened = HomeSessionDataStore(disk, backgroundScope)
        assertEquals(session(1), reopened.read(a)); assertEquals(session(2), reopened.read(b))
        assertNull(reopened.read("c".repeat(64)))
    }

    @Test fun aNewerEditDuringAnActiveCommitIsNotDropped() = runTest {
        val disk = MemoryStore().apply { gate = CompletableDeferred() }
        val repository = HomeSessionDataStore(disk, backgroundScope)
        repository.remember(a, session(1)); repository.flush(); runCurrent()
        repository.remember(a, session(2)); repository.flush(); runCurrent()
        disk.gate!!.complete(Unit); runCurrent()
        assertEquals(session(2), HomeSessionDataStore(disk, backgroundScope).read(a))
    }

    @Test fun aFailedCommitCanBeFlushedAgainWithoutAffectingTheScreen() = runTest {
        val disk = MemoryStore().apply { fail = true }
        val repository = HomeSessionDataStore(disk, backgroundScope)
        repository.remember(a, session(7)); repository.flush(); runCurrent()
        assertEquals(session(7), repository.read(a))
        disk.fail = false; repository.flush(); runCurrent()
        assertEquals(session(7), HomeSessionDataStore(disk, backgroundScope).read(a))
    }

    @Test fun malformedRecordsAndForeignPreferenceKeysDoNotBreakOtherSources() = runTest {
        val disk = MemoryStore(mutablePreferencesOf(stringPreferencesKey("session_$a") to "corrupt",
            stringPreferencesKey("future") to "keep"))
        val repository = HomeSessionDataStore(disk, backgroundScope)
        assertNull(repository.read(a))
        repository.remember("not-a-source-key", session(0))
        repository.remember(b, session(2)); repository.flush(); runCurrent()
        assertEquals("keep", disk.data.first()[stringPreferencesKey("future")])
        assertEquals(session(2), HomeSessionDataStore(disk, backgroundScope).read(b))
        assertEquals(1, disk.writes)
    }

    @Test fun retainedSourceRecordsAreBounded() = runTest {
        val disk = MemoryStore()
        val repository = HomeSessionDataStore(disk, backgroundScope)
        repeat(50) { repository.remember(it.toString(16).padStart(64, '0'), session(it)); repository.flush(); runCurrent() }
        assertEquals(32, disk.data.first().asMap().keys.count { it.name.startsWith("session_") })
        assertEquals(session(49), HomeSessionDataStore(disk, backgroundScope).read(49.toString(16).padStart(64, '0')))
    }

    @Test fun actualPreferencesSurviveClosingAndReopeningTheWriter() = runBlocking {
        repeat(5) { iteration ->
        val file = temporary.root.resolve("home_session_$iteration.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val expected = session(40)
        try {
            val disk = PreferenceDataStoreFactory.create(scope = scope) { file }
            val repository = HomeSessionDataStore(disk, scope)
            repository.remember(a, expected); repository.flush()
            withTimeout(5000) { disk.data.first { it[stringPreferencesKey("session_$a")] != null } }
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = HomeSessionDataStore(PreferenceDataStoreFactory.create(scope = scope) { file }, scope)
            assertEquals(expected, repository.read(a))
            assertNull(repository.read(b))
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }

    private fun session(index: Int) = HomeBrowseSession("group", focusedChannelId = "id-$index",
        position = HomeGridPosition("id-${index / 3 * 3}", 5, 3))

    private class MemoryStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val values = MutableStateFlow(initial)
        private val mutex = Mutex()
        override val data: Flow<Preferences> = values
        var writes = 0
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            gate?.await()
            if (fail) throw IOException("Write unavailable")
            transform(values.value).also { values.value = it; writes++ }
        }
    }
}