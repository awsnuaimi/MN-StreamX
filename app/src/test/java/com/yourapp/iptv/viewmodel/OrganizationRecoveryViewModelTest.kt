package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.organization.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.*

@OptIn(ExperimentalCoroutinesApi::class)
class OrganizationRecoveryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After fun cleanup() {
        Dispatchers.resetMain()
    }

    private val old = OrganizationNames.groupKey("Old provider group")
    private val replacement = OrganizationNames.groupKey("AR SYRIA HD")

    @Test fun previewIsReadOnlyAndRelinkingCanBeUndoneBeforeSaving() =
        runTest(dispatcher) {
            val repo = OrganizationMemory(
                OrganizationConfig(
                    enabled = true,
                    groupOverrides = mapOf(old to "german-news"),
                    channelOverrides = mapOf("s1" to "polish-news")
                )
            )
            val vm = OrganizationEditorViewModel(
                repo, OrganizationChannelsFixture(), dispatcher
            )
            advanceUntilIdle()
            val before = requireNotNull(vm.uiState.value.value)

            vm.previewRecovery(old, replacement)
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.recoveryPreview)
            assertEquals(before, vm.uiState.value.value)
            assertEquals(0, repo.writes)

            assertTrue(vm.applyRecovery())
            assertEquals(
                "german-news",
                vm.uiState.value.value!!.groupOverrides[replacement]
            )
            assertEquals(
                "polish-news",
                vm.uiState.value.preview!!.assignments.getValue("s1").folderId
            )
            assertTrue(vm.uiState.value.canUndoRecovery)

            vm.undoRecovery()
            advanceUntilIdle()

            assertEquals(
                before.groupOverrides,
                vm.uiState.value.value!!.groupOverrides
            )
            assertFalse(vm.uiState.value.canUndoRecovery)
            assertEquals(0, repo.writes)
        }

    @Test fun cancelledOrConflictingPreviewsCannotModifyTheDraft() =
        runTest(dispatcher) {
            val repo = OrganizationMemory(
                OrganizationConfig(
                    enabled = true,
                    groupOverrides = mapOf(
                        old to "german-news",
                        replacement to "polish-news"
                    )
                )
            )
            val vm = OrganizationEditorViewModel(
                repo, OrganizationChannelsFixture(), dispatcher
            )
            advanceUntilIdle()
            val before = vm.uiState.value.value

            vm.previewRecovery(old, replacement)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.recoveryError)
            assertFalse(vm.applyRecovery())

            vm.cancelRecovery()

            assertEquals(before, vm.uiState.value.value)
            assertEquals(0, repo.writes)
        }
}