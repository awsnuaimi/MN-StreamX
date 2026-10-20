package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.organization.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.*

@OptIn(ExperimentalCoroutinesApi::class)
class OrganizationEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun previewAndManualEditsStayLocalUntilApplyAndDuplicateSaveClicksAreIgnored() = runTest(dispatcher) {
        val repository = OrganizationMemory(OrganizationConfig(sourceKey = "test", enabled = false))
        val model = OrganizationEditorViewModel(repository, OrganizationChannelsFixture(), dispatcher)
        advanceUntilIdle()
        assertTrue(model.uiState.value.preview!!.tree.enabled)
        assertFalse(model.uiState.value.value!!.enabled)
        model.enable(true); advanceUntilIdle()
        model.folder(null, "Family", null); advanceUntilIdle()
        val id = model.uiState.value.value!!.folders.single { it.name == "Family" }.id
        model.moveGroup(OrganizationNames.groupKey("AR SYRIA HD"), id); advanceUntilIdle()
        assertEquals(2, model.uiState.value.preview!!.tree.counts[id])
        assertEquals(0, repository.writes)
        model.save(); model.save(); advanceUntilIdle()
        assertEquals(1, repository.writes)
        assertTrue(repository.value.value.enabled)
        assertTrue(model.uiState.value.saved)
    }
    @Test fun rapidRuleEditsKeepBothRulesAndIndividualAssignmentsWin() = runTest(dispatcher) {
        val repository = OrganizationMemory()
        val model = OrganizationEditorViewModel(repository, OrganizationChannelsFixture(), dispatcher)
        advanceUntilIdle()
        model.moveGroup(OrganizationNames.groupKey("AR SYRIA HD"), "german-general")
        model.moveChannel("s1", "polish-general")
        advanceUntilIdle()
        val preview = model.uiState.value.preview!!
        assertEquals("polish-general", preview.assignments.getValue("s1").folderId)
        assertEquals("german-general", preview.assignments.getValue("s2").folderId)
        assertEquals(0, repository.writes)
        model.moveChannel("s1", null); advanceUntilIdle()
        assertEquals("german-general", model.uiState.value.preview!!.assignments.getValue("s1").folderId)
    }
    @Test fun invalidFoldersDoNotChangeTheDraftAndReadOrSaveFailuresAreExplicit() = runTest(dispatcher) {
        val repository = OrganizationMemory()
        val model = OrganizationEditorViewModel(repository, OrganizationChannelsFixture(), dispatcher)
        advanceUntilIdle()
        val before = model.uiState.value.value
        model.folder("arabic", "Bad cycle", "arabic-syria")
        assertEquals(OrganizationIssue.INVALID_FOLDER, model.uiState.value.issue)
        assertEquals(before, model.uiState.value.value)
        model.save(); advanceUntilIdle(); assertEquals(0, repository.writes)
        model.enable(true); advanceUntilIdle(); repository.failSave = true
        model.save(); advanceUntilIdle()
        assertEquals(OrganizationIssue.SAVE, model.uiState.value.issue)
        assertFalse(model.uiState.value.saved)
        val unreadable = OrganizationMemory().apply { failRead = true }
        val blocked = OrganizationEditorViewModel(unreadable, OrganizationChannelsFixture(), dispatcher)
        advanceUntilIdle(); blocked.save(); advanceUntilIdle()
        assertEquals(OrganizationIssue.READ, blocked.uiState.value.issue)
        assertEquals(0, unreadable.writes)
    }
    @Test fun resetClearsManualRulesAndCustomFoldersOnlyInTheDraft() = runTest(dispatcher) {
        val initial = OrganizationConfig(sourceKey = "test", enabled = true,
            channelOverrides = mapOf("s1" to "german-general"))
        val repository = OrganizationMemory(initial)
        val model = OrganizationEditorViewModel(repository, OrganizationChannelsFixture(), dispatcher)
        advanceUntilIdle(); model.folder(null, "Custom", null); advanceUntilIdle()
        model.reset(); advanceUntilIdle()
        assertTrue(model.uiState.value.value!!.channelOverrides.isEmpty())
        assertFalse(model.uiState.value.value!!.folders.any { it.name == "Custom" })
        assertEquals(initial, repository.value.value)
    }
}