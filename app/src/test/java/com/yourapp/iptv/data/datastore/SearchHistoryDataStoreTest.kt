package com.yourapp.iptv.data.datastore

import androidx.datastore.preferences.core.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SearchHistoryDataStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val a = "a".repeat(64)
    private val b = "b".repeat(64)
    private suspend fun withStore(block: suspend (androidx.datastore.core.DataStore<Preferences>) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try { block(PreferenceDataStoreFactory.create(scope = scope) { temporary.newFolder().resolve("search.preferences_pb") }) }
        finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
    @Test fun queriesArePerSourceDeduplicatedAndClearingOneSourcePreservesTheOther() = runBlocking {
        withStore { disk ->
            val repo = SearchHistoryDataStore(disk)
            repo.record(a, "Straße"); repo.record(a, "Other"); repo.record(a, " STRASSE ")
            repo.record(b, "Independent")
            assertEquals(listOf("STRASSE", "Other"), repo.observe(a).first())
            repo.clear(a)
            assertTrue(repo.observe(a).first().isEmpty())
            assertEquals(listOf("Independent"), repo.observe(b).first())
        }
    }
    @Test fun historySurvivesAnActualFileReopening() = runBlocking {
        val file = temporary.root.resolve("reopen.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = SearchHistoryDataStore(PreferenceDataStoreFactory.create(scope = scope) { file })
            repo.record(a, "عنوان عربي"); repo.record(b, "German title")
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = SearchHistoryDataStore(PreferenceDataStoreFactory.create(scope = scope) { file })
            assertEquals(listOf("عنوان عربي"), repo.observe(a).first())
            assertEquals(listOf("German title"), repo.observe(b).first())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
    @Test fun concurrentWritesRemainBoundedAndDoNotLoseIndependentSourceUpdates() = runBlocking {
        withStore { disk ->
            val repo = SearchHistoryDataStore(disk)
            coroutineScope { (1..30).map { i -> async { repo.record(a, "Title $i") } }.awaitAll() }
            assertEquals(20, repo.observe(a).first().size)
            coroutineScope { listOf(async { repo.record(a, "First") }, async { repo.record(b, "Second") }).awaitAll() }
            assertEquals("First", repo.observe(a).first().first())
            assertEquals(listOf("Second"), repo.observe(b).first())
        }
    }
    @Test fun invalidOrDuplicateStoredRowsAreReportedAndCanBeExplicitlyCleared() = runBlocking {
        withStore { disk ->
            val key = stringPreferencesKey("search_$a")
            val invalid = """{"version":1,"queries":["One","ONE"]}"""
            disk.edit { it[key] = invalid }
            val repo = SearchHistoryDataStore(disk)
            assertTrue(runCatching { repo.observe(a).first() }.exceptionOrNull() is IOException)
            assertTrue(runCatching { repo.record(a, "New") }.isFailure)
            assertEquals(invalid, disk.data.first()[key])
            repo.clear(a); repo.record(a, "Recovered")
            assertEquals(listOf("Recovered"), repo.observe(a).first())
        }
    }
    @Test fun onlyThirtyTwoSourceHistoriesAreRetainedAndUnrelatedDataIsPreserved() = runBlocking {
        withStore { disk ->
            disk.edit { it[stringPreferencesKey("unrelated")] = "preserve" }
            val repo = SearchHistoryDataStore(disk)
            for (i in 1..34) repo.record(i.toString(16).padStart(64, '0'), "Title $i")
            assertEquals(32, disk.data.first().asMap().keys.count { it.name.startsWith("search_") })
            assertEquals("preserve", disk.data.first()[stringPreferencesKey("unrelated")])
            assertTrue(repo.observe("1".padStart(64, '0')).first().isEmpty())
        }
    }
    @Test fun invalidKeysAndBlankQueriesCannotCreatePreferenceEntries() = runBlocking {
        withStore { disk ->
            val repo = SearchHistoryDataStore(disk)
            assertTrue(runCatching { repo.record("invalid", "Title") }.isFailure)
            repo.record(a, "---")
            assertTrue(disk.data.first().asMap().isEmpty())
        }
    }
}