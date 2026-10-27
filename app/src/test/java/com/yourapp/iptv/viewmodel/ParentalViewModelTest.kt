package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.data.parental.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.*
import com.yourapp.iptv.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ParentalViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    private val access = Access(ParentalState(ready = true,configured = true,unlocked = true))
    private val accounts = SearchAccounts()
    private var requests = 0
    private var loadGroups: suspend (AppSettings) -> List<String> = { listOf("Group ${it.xtreamUsername}") }
    private fun model() = ParentalViewModel(access,accounts,object : ParentalCatalog {
        override suspend fun groups(source: AppSettings): List<String> { requests++;return loadGroups(source) }
    }).also { owner.put("root",it) }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { owner.clear();dispatcher.scheduler.runCurrent();Dispatchers.resetMain() }
    @Test fun enteringTheAppDoesNotLoadTheAdministrativeCatalog() = runTest(dispatcher) {
        val model = model();runCurrent();assertEquals(0,requests)
        model.loadGroups();runCurrent();assertEquals(listOf("Group A"),model.editor.value.groups)
    }
    @Test fun sourceChangeDiscardsLateGroupResultsAndRelocksAccess() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>();val release = CompletableDeferred<Unit>()
        loadGroups = { withContext(NonCancellable) { started.complete(Unit);release.await() };listOf("Old group") }
        val model = model();runCurrent();model.loadGroups();runCurrent();assertTrue(started.isCompleted)
        accounts.values.value = searchAccount("B");runCurrent();release.complete(Unit);runCurrent()
        assertEquals(accounts.key,model.editor.value.sourceKey);assertTrue(model.editor.value.groups.isEmpty())
        assertFalse(model.access.value.unlocked)
    }
    @Test fun aPickerResultIsRetainedAcrossBackgroundButExpiresAfterTenMinutes() = runTest(dispatcher) {
        val model = model();runCurrent();val token = model.beginExternal();model.background();runCurrent()
        model.finishExternal(token);assertEquals(false,model.externalFlow.value?.awaitingResult)
        val revision = model.renderReset.value
        advanceTimeBy(10 * 60_000L + 1);runCurrent()
        assertNull(model.externalFlow.value);assertTrue(model.renderReset.value > revision);assertFalse(model.access.value.unlocked)
    }
    @Test fun onlyTheMatchingFlowCanFinishAndPermissionToInstallerCanReuseItsTicket() = runTest(dispatcher) {
        val model = model();runCurrent();val token = requireNotNull(model.beginExternal())
        model.background();model.finishExternal(token + 1);assertEquals(true,model.externalFlow.value?.awaitingResult)
        assertEquals(token,model.beginExternal(token));model.finishExternal(token)
        model.consumeExternal(token);assertNotNull(model.externalFlow.value)
        access.state.value = access.state.value.copy(unlocked = true)
        model.consumeExternal(token);assertNull(model.externalFlow.value)
    }
}