package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.HomeBrowseSession
import com.yourapp.iptv.domain.repository.HomeSessionRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeSessionControllerTest {
    @Test fun disabledRestorationNeverReadsOrWritesAndDoesNotReplayOnEnabling() = runTest {
        val repo = Repository()
        val seen = mutableListOf<HomeBrowseSession?>()
        val controller = HomeSessionController(backgroundScope, repo, seen::add)
        controller.open("a"); controller.configure(false); runCurrent()
        controller.remember(HomeBrowseSession(group = "News"))
        controller.configure(true); runCurrent()
        assertEquals(0, repo.reads); assertTrue(repo.writes.isEmpty())
        assertEquals(listOf<HomeBrowseSession?>(null), seen)
    }

    @Test fun restoreWaitsForThePreferenceAndRunsOncePerSource() = runTest {
        val repo = Repository()
        val seen = mutableListOf<HomeBrowseSession?>()
        val controller = HomeSessionController(backgroundScope, repo, seen::add)
        controller.open("a"); runCurrent(); assertEquals(0, repo.reads)
        controller.configure(true); runCurrent()
        repo.results.getValue("a").complete(HomeBrowseSession(group = "News")); runCurrent()
        controller.configure(true); runCurrent()
        assertEquals(1, repo.reads); assertEquals("News", seen.single()?.group)
    }

    @Test fun lateSavedDataCannotUndoUserNavigation() = runTest {
        val repo = Repository()
        val seen = mutableListOf<HomeBrowseSession?>()
        val controller = HomeSessionController(backgroundScope, repo, seen::add)
        controller.open("a"); controller.configure(true); runCurrent()
        val chosen = HomeBrowseSession(group = "chosen")
        controller.remember(chosen)
        repo.results.getValue("a").complete(HomeBrowseSession(group = "old")); runCurrent()
        assertNull(seen.single())
        assertEquals("a" to chosen, repo.writes.single())
    }

    @Test fun aCancelledOldSourceResponseCannotRestoreIntoTheNewSource() = runTest {
        val repo = Repository()
        val seen = mutableListOf<HomeBrowseSession?>()
        val controller = HomeSessionController(backgroundScope, repo, seen::add)
        controller.open("a"); controller.configure(true); runCurrent()
        controller.open("b"); runCurrent()
        repo.results.getValue("b").complete(HomeBrowseSession(group = "new")); runCurrent()
        repo.results.getValue("a").complete(HomeBrowseSession(group = "old")); runCurrent()
        assertEquals(listOf("new"), seen.map { it?.group })
        controller.remember(HomeBrowseSession(group = "B"))
        assertEquals("b", repo.writes.single().first)
    }

    @Test fun disablingWhileReadingInvalidatesEvenANonCooperativeRead() = runTest {
        val repo = Repository()
        val seen = mutableListOf<HomeBrowseSession?>()
        val controller = HomeSessionController(backgroundScope, repo, seen::add)
        controller.open("a"); controller.configure(true); runCurrent()
        controller.configure(false)
        repo.results.getValue("a").complete(HomeBrowseSession(group = "old")); runCurrent()
        assertEquals(listOf<HomeBrowseSession?>(null), seen)
    }

    private class Repository : HomeSessionRepository {
        var reads = 0
        val results = mutableMapOf<String, CompletableDeferred<HomeBrowseSession?>>()
        val writes = mutableListOf<Pair<String, HomeBrowseSession>>()
        override suspend fun read(sourceKey: String): HomeBrowseSession? {
            reads++
            val result = results.getOrPut(sourceKey) { CompletableDeferred() }
            return withContext(NonCancellable) { result.await() }
        }
        override fun remember(sourceKey: String, value: HomeBrowseSession) { writes += sourceKey to value }
        override fun flush() = Unit
    }
}