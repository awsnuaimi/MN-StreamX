package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.di.DefaultDispatcher
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.domain.repository.ChannelRepository
import com.yourapp.iptv.domain.repository.OrganizationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject

enum class OrganizationIssue { READ, SAVE, INVALID_FOLDER }

data class OrganizationEditorState(
    val value: OrganizationConfig? = null,
    val preview: OrganizedChannels? = null,
    val loading: Boolean = true,
    val computing: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val dirty: Boolean = false,
    val issue: OrganizationIssue? = null,
    val selectedGroups: Set<String> = emptySet(),
    val recoveryBusy: Boolean = false,
    val recoveryError: Boolean = false,
    val recoveryPreview: GroupRecoveryPreview? = null,
    val canUndoRecovery: Boolean = false
)

@HiltViewModel
class OrganizationEditorViewModel @Inject constructor(
    private val repository: OrganizationRepository,
    private val channels: ChannelRepository,
    @DefaultDispatcher private val background: CoroutineDispatcher
) : ViewModel() {
    private val state = MutableStateFlow(OrganizationEditorState())
    val uiState = state.asStateFlow()

    private var source = emptyList<Channel>()
    private var sourceGroupKeys = emptySet<String>()
    private var sourceKeysByName = emptyMap<String?, String>()

    private var loadJob: Job? = null
    private var previewJob: Job? = null
    private var recoveryJob: Job? = null

    private var recoveryPlan: GroupRelinkPlan? = null
    private var recoveryResult: OrganizedChannels? = null
    private var recoveryUndo: GroupRelinkUndo? = null

    init {
        load()
    }

    fun load() {
        if (state.value.saving || loadJob?.isActive == true) return

        previewJob?.cancel()
        cancelRecovery()
        recoveryUndo = null
        state.value = OrganizationEditorState()

        loadJob = viewModelScope.launch {
            try {
                val config = repository.observe().first()
                val stored = channels.getChannels()

                val computed = withContext(background) {
                    val worker = currentCoroutineContext()
                    val preview = ChannelOrganizer.project(
                        stored,
                        config.copy(enabled = true)
                    ) {
                        worker.ensureActive()
                    }

                    preview to stored.map { it.group }.distinct()
                        .associateWith(OrganizationNames::groupKey)
                }

                val preview = computed.first
                source = stored
                sourceKeysByName = computed.second
                sourceGroupKeys = sourceKeysByName.values.toSet()

                state.value = OrganizationEditorState(
                    config.copy(folders = preview.tree.folders),
                    preview,
                    loading = false
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.value = OrganizationEditorState(
                    loading = false,
                    issue = OrganizationIssue.READ
                )
            }
        }
    }

    fun enable(value: Boolean) = edit {
        copy(enabled = value)
    }

    fun moveGroup(key: String, folder: String?) =
        moveGroups(setOf(key), folder)

    fun moveGroups(keys: Set<String>, folder: String?) {
        if (keys.isEmpty()) return
        edit {
            FolderEdits.assignGroups(this, sourceGroupKeys, keys, folder)
        }
    }

    fun setFolderSort(mode: FolderSort) {
        if (state.value.value?.folderSort != mode) {
            edit { copy(folderSort = mode) }
        }
    }

    fun toggleGroupSelection(key: String) {
        if (state.value.saving || key !in sourceGroupKeys) return

        state.update {
            it.copy(
                selectedGroups = it.selectedGroups.toMutableSet().apply {
                    if (!add(key)) remove(key)
                }
            )
        }
    }

    fun selectGroupResults(keys: List<String>) {
        if (state.value.saving) return
        state.update {
            it.copy(
                selectedGroups = it.selectedGroups +
                    keys.filter { key -> key in sourceGroupKeys }
            )
        }
    }

    fun clearGroupSelection() {
        if (!state.value.saving) {
            state.update { it.copy(selectedGroups = emptySet()) }
        }
    }

    fun moveSelectedGroups(folder: String?): Boolean {
        val before = state.value
        moveGroups(before.selectedGroups, folder)

        val accepted = state.value.value !== before.value &&
            state.value.issue == null

        if (accepted) clearGroupSelection()
        return accepted
    }

    fun moveChannel(id: String, folder: String?) = edit {
        require(source.any { it.id == id })
        require(folder == null || folders.any { it.id == folder })

        copy(
            channelOverrides = channelOverrides.toMutableMap().apply {
                if (folder == null) remove(id) else put(id, folder)
            }
        )
    }

    fun folder(id: String?, name: String, parent: String?) = edit {
        FolderEdits.upsert(
            this,
            id ?: "custom-${UUID.randomUUID()}",
            name,
            parent
        )
    }

    fun reorder(id: String, direction: Int) = edit {
        FolderEdits.move(this, id, direction)
    }

    fun reset() {
        edit {
            OrganizationConfig(
                sourceKey,
                enabled,
                revision = revision,
                folderSort = folderSort
            )
        }
        clearGroupSelection()
    }

    private fun edit(change: OrganizationConfig.() -> OrganizationConfig) {
        val current = state.value
        if (current.loading || current.saving || current.recoveryBusy ||
            current.value == null || current.issue == OrganizationIssue.READ
        ) return

        val updated = try {
            FolderEdits.validate(current.value.change())
        } catch (_: IllegalArgumentException) {
            state.update { it.copy(issue = OrganizationIssue.INVALID_FOLDER) }
            return
        }

        if (updated == current.value) {
            state.update { it.copy(issue = null) }
            return
        }

        cancelRecovery()
        recoveryUndo = null
        previewJob?.cancel()

        state.update {
            it.copy(
                value = updated,
                computing = true,
                dirty = true,
                saved = false,
                issue = null,
                canUndoRecovery = false
            )
        }

        previewJob = viewModelScope.launch {
            try {
                val preview = withContext(background) {
                    val worker = currentCoroutineContext()
                    ChannelOrganizer.project(
                        source,
                        updated.copy(enabled = true)
                    ) {
                        worker.ensureActive()
                    }
                }

                state.update {
                    it.copy(
                        value = updated.copy(folders = preview.tree.folders),
                        preview = preview,
                        computing = false
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.update {
                    it.copy(
                        computing = false,
                        issue = OrganizationIssue.INVALID_FOLDER
                    )
                }
            }
        }
    }

    fun save() {
        val current = state.value
        if (current.loading || current.computing || current.saving ||
            current.recoveryBusy || current.recoveryPreview != null ||
            current.value == null || current.issue != null
        ) return

        state.update { it.copy(saving = true, issue = null) }

        viewModelScope.launch {
            try {
                repository.save(current.value)
                state.update {
                    it.copy(saving = false, saved = true, dirty = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.update {
                    it.copy(saving = false, issue = OrganizationIssue.SAVE)
                }
            }
        }
    }

    fun cancelRecovery() {
        recoveryJob?.cancel()
        recoveryJob = null
        recoveryPlan = null
        recoveryResult = null

        state.update {
            it.copy(
                recoveryBusy = false,
                recoveryError = false,
                recoveryPreview = null
            )
        }
    }

    fun previewRecovery(oldKey: String, newKey: String) {
        val current = state.value
        val value = current.value ?: return
        val before = current.preview ?: return

        if (current.loading || current.computing || current.saving ||
            current.recoveryBusy || current.issue != null ||
            before.channels.isEmpty()
        ) return

        cancelRecovery()
        state.update { it.copy(recoveryBusy = true) }

        recoveryJob = viewModelScope.launch {
            try {
                val prepared = withContext(background) {
                    val worker = currentCoroutineContext()
                    val plan = OrganizationRecovery.plan(
                        value,
                        before.groups,
                        oldKey,
                        newKey
                    )

                    val after = ChannelOrganizer.project(
                        source,
                        plan.after.copy(enabled = true)
                    ) {
                        worker.ensureActive()
                    }

                    val members = before.channels.filter {
                        sourceKeysByName[it.group] == newKey
                    }

                    val changed = members.count {
                        before.assignments.getValue(it.id).folderId !=
                            after.assignments.getValue(it.id).folderId
                    }

                    val protectedChannels = members.count {
                        before.assignments.getValue(it.id).reason ==
                            AssignmentReason.CHANNEL_RULE
                    }

                    val name = before.groups.first { it.key == newKey }.name

                    Triple(
                        plan,
                        after,
                        GroupRecoveryPreview(
                            oldKey,
                            name,
                            plan.folderId,
                            members.size,
                            changed,
                            protectedChannels
                        )
                    )
                }

                if (state.value.value != value) {
                    state.update {
                        it.copy(recoveryBusy = false, recoveryError = true)
                    }
                    return@launch
                }

                recoveryPlan = prepared.first
                recoveryResult = prepared.second

                state.update {
                    it.copy(
                        recoveryBusy = false,
                        recoveryPreview = prepared.third
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.update {
                    it.copy(recoveryBusy = false, recoveryError = true)
                }
            }
        }
    }

    fun applyRecovery(): Boolean {
        val current = state.value
        val plan = recoveryPlan ?: return false
        val preview = recoveryResult ?: return false

        if (current.value != plan.before || current.computing ||
            current.saving || current.recoveryBusy
        ) {
            cancelRecovery()
            state.update { it.copy(recoveryError = true) }
            return false
        }

        recoveryUndo = GroupRelinkUndo(
            plan.before.sourceKey,
            plan.oldKey,
            plan.newKey,
            plan.folderId
        )

        recoveryPlan = null
        recoveryResult = null

        state.value = current.copy(
            value = plan.after.copy(folders = preview.tree.folders),
            preview = preview,
            dirty = true,
            saved = false,
            issue = null,
            recoveryPreview = null,
            recoveryError = false,
            canUndoRecovery = true,
            selectedGroups = emptySet()
        )

        return true
    }

    fun undoRecovery() {
        val token = recoveryUndo ?: return
        edit { OrganizationRecovery.undo(this, token) }
    }
}