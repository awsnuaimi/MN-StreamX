package com.yourapp.iptv.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class StartupPlaybackDataStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun newInstallDefaultsToDisabledAndDoesNotWriteOnRead() = runTest {
        val disk = MemoryStore()
        assertFalse(StartupPlaybackDataStore(disk).observe().first())
        assertEquals(0, disk.writes)
    }

    @Test fun enablingAndDisablingPreserveUnrelatedPreferences() = runTest {
        val other = stringPreferencesKey("future")
        val disk = MemoryStore(mutablePreferencesOf(other to "keep"))
        val repository = StartupPlaybackDataStore(disk)
        repository.save(true); assertTrue(repository.observe().first())
        repository.save(false); assertFalse(repository.observe().first())
        assertEquals("keep", disk.data.first()[other])
    }

    @Test fun aWrongTypedPreferenceFailsClosedInsteadOfEnablingPlayback() = runTest {
        val disk = MemoryStore(mutablePreferencesOf(stringPreferencesKey("resume_last_channel") to "true"))
        assertFalse(StartupPlaybackDataStore(disk).observe().first())
    }

    @Test fun writeFailuresAreReportedAndDoNotReplaceTheExistingValue() = runTest {
        val disk = MemoryStore().apply { fail = true }
        try { StartupPlaybackDataStore(disk).save(true); fail("Expected write failure") }
        catch (_: IOException) { }
        assertFalse(StartupPlaybackDataStore(disk).observe().first())
    }

    @Test fun actualPreferenceSurvivesClosingAndReopeningTheAppStore() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val file = temporary.root.resolve("startup.preferences_pb")
        for (enabled in listOf(true, false)) {
            var scope = CoroutineScope(SupervisorJob() + dispatcher)
            try {
                val disk = PreferenceDataStoreFactory.create(scope = scope) { file }
                StartupPlaybackDataStore(disk).save(enabled)
                assertTrue(file.isFile && file.length() > 0)
            } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
            scope = CoroutineScope(SupervisorJob() + dispatcher)
            try {
                val reopened = PreferenceDataStoreFactory.create(scope = scope) { file }
                assertEquals(enabled, StartupPlaybackDataStore(reopened).observe().first())
            } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }

    private class MemoryStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val values = MutableStateFlow(initial)
        override val data: Flow<Preferences> = values
        var writes = 0
        var fail = false
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            if (fail) throw IOException("Write unavailable")
            return transform(values.value).also { values.value = it; writes++ }
        }
    }
}