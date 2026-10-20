package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.organization.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.*

@OptIn(ExperimentalCoroutinesApi::class)
class OrganizationBatchTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private val syria = OrganizationNames.groupKey("AR SYRIA HD")
    private val egypt = OrganizationNames.groupKey("AR EGYPT")

    @Test fun bulkMoveIsOneDraftChangeAndIndividualRulesKeepPriority() = runTest(dispatcher) {
        val repo = OrganizationMemory(OrganizationConfig(enabled = true, channelOverrides = mapOf("s1" to "polish-general")))
        val vm = OrganizationEditorViewModel(repo, OrganizationChannelsFixture(), dispatcher)
        advanceUntilIdle()
        vm.selectGroupResults(listOf(syria)); vm.selectGroupResults(listOf(syria, egypt))
        assertEquals(setOf(syria, egypt), vm.uiState.value.selectedGroups)
        assertTrue(vm.moveSelectedGroups("german-general")); advanceUntilIdle()
        val state = vm.uiState.value
        val preview = requireNotNull(state.preview)
        assertEquals(2, state.value!!.groupOverrides.size)
        assertEquals("polish-general", preview.assignments.getValue("s1").folderId)
        assertEquals("german-general", preview.assignments.getValue("s2").folderId)
        assertEquals("german-general", preview.assignments.getValue("e1").folderId)
        assertTrue(state.selectedGroups.isEmpty()); assertEquals(0, repo.writes)
        vm.save(); vm.save(); advanceUntilIdle(); assertEquals(1, repo.writes)
    }
    @Test fun invalidBatchCannotPartiallyMoveValidGroupsOrLoseTheDraft() = runTest(dispatcher) {
        val vm = OrganizationEditorViewModel(OrganizationMemory(), OrganizationChannelsFixture(), dispatcher)
        advanceUntilIdle(); val before = vm.uiState.value.value
        vm.moveGroups(setOf(syria, "unknown-group"), "german-general"); advanceUntilIdle()
        assertEquals(before, vm.uiState.value.value)
        assertEquals(OrganizationIssue.INVALID_FOLDER, vm.uiState.value.issue)
        vm.moveGroups(setOf(syria, egypt), "missing-folder"); advanceUntilIdle()
        assertEquals(before, vm.uiState.value.value)
        vm.selectGroupResults(listOf(syria))
        assertFalse(vm.moveSelectedGroups("missing-folder"))
        assertEquals(setOf(syria), vm.uiState.value.selectedGroups)
    }
    @Test fun clearingBatchAssignmentsRetainsPerChannelRulesAndUnselectedGroups() = runTest(dispatcher) {
        val initial = OrganizationConfig(enabled = true, groupOverrides = mapOf(syria to "german-general", egypt to "german-news"),
            channelOverrides = mapOf("s1" to "polish-news"))
        val vm = OrganizationEditorViewModel(OrganizationMemory(initial), OrganizationChannelsFixture(), dispatcher)
        advanceUntilIdle(); vm.toggleGroupSelection(syria); vm.moveSelectedGroups(null); advanceUntilIdle()
        assertFalse(vm.uiState.value.value!!.groupOverrides.containsKey(syria))
        assertEquals("german-news", vm.uiState.value.value!!.groupOverrides[egypt])
        assertEquals("polish-news", vm.uiState.value.preview!!.assignments.getValue("s1").folderId)
        assertEquals("arabic-syria", vm.uiState.value.preview!!.assignments.getValue("s2").folderId)
    }
    @Test fun selectionIsTransientAndEmptyMovesDoNotMarkTheDraftDirty() = runTest(dispatcher) {
        val repo = OrganizationMemory(); val vm = OrganizationEditorViewModel(repo, OrganizationChannelsFixture(), dispatcher)
        advanceUntilIdle(); vm.moveSelectedGroups(null)
        vm.setFolderSort(FolderSort.SAVED)
        assertFalse(vm.uiState.value.dirty)
        vm.selectGroupResults(listOf(syria, "missing")); vm.toggleGroupSelection(egypt); vm.toggleGroupSelection(syria)
        assertEquals(setOf(egypt), vm.uiState.value.selectedGroups)
        vm.clearGroupSelection(); assertTrue(vm.uiState.value.selectedGroups.isEmpty())
        assertFalse(vm.uiState.value.dirty); assertEquals(0, repo.writes)
    }
    @Test fun changingDisplaySortDoesNotReplaceManualFolderOrderAndIsSavedOnlyOnApply() = runTest(dispatcher) {
        val repo = OrganizationMemory(); val vm = OrganizationEditorViewModel(repo, OrganizationChannelsFixture(), dispatcher)
        advanceUntilIdle(); val before = vm.uiState.value.value!!.folders
        vm.setFolderSort(FolderSort.COUNT); advanceUntilIdle()
        assertEquals(FolderSort.COUNT, vm.uiState.value.preview!!.tree.folderSort)
        assertEquals(before, vm.uiState.value.value!!.folders)
        assertEquals(FolderSort.SAVED, repo.value.value.folderSort)
        vm.save(); advanceUntilIdle(); assertEquals(FolderSort.COUNT, repo.value.value.folderSort)
    }
}