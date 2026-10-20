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
data class OrganizationEditorState(val value: OrganizationConfig? = null,
    val preview: OrganizedChannels? = null, val loading: Boolean = true, val computing: Boolean = false,
    val saving: Boolean = false, val saved: Boolean = false, val dirty: Boolean = false,
    val issue: OrganizationIssue? = null, val selectedGroups: Set<String> = emptySet())

@HiltViewModel
class OrganizationEditorViewModel @Inject constructor(private val repository: OrganizationRepository,
    private val channels: ChannelRepository, @DefaultDispatcher private val background: CoroutineDispatcher) : ViewModel() {
    private val state = MutableStateFlow(OrganizationEditorState())
    val uiState = state.asStateFlow()
    private var source = emptyList<Channel>()
    private var sourceGroupKeys = emptySet<String>()
    private var loadJob: Job? = null
    private var previewJob: Job? = null
    init { load() }
    fun load() {
        if (state.value.saving || loadJob?.isActive == true) return
        previewJob?.cancel(); state.value = OrganizationEditorState()
        loadJob = viewModelScope.launch {
            try {
                val config = repository.observe().first()
                val stored = channels.getChannels()
                val preview = withContext(background) { ChannelOrganizer.project(stored, config.copy(enabled = true)) }
                source = stored
                sourceGroupKeys = stored.map { it.group }.distinct().map(OrganizationNames::groupKey).toSet()
                state.value = OrganizationEditorState(config.copy(folders = preview.tree.folders), preview, loading = false)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { state.value = OrganizationEditorState(loading = false, issue = OrganizationIssue.READ) }
        }
    }
    fun enable(value: Boolean) = edit { copy(enabled = value) }
    fun moveGroup(key: String, folder: String?) = moveGroups(setOf(key), folder)
    fun moveGroups(keys: Set<String>, folder: String?) {
        if (keys.isEmpty()) return
        edit { FolderEdits.assignGroups(this, sourceGroupKeys, keys, folder) }
    }
    fun setFolderSort(mode: FolderSort) {
        if (state.value.value?.folderSort != mode) edit { copy(folderSort = mode) }
    }
    fun toggleGroupSelection(key: String) {
        if (state.value.saving || key !in sourceGroupKeys) return
        state.update { it.copy(selectedGroups = it.selectedGroups.toMutableSet().apply { if (!add(key)) remove(key) }) }
    }
    fun selectGroupResults(keys: List<String>) {
        if (state.value.saving) return
        state.update { it.copy(selectedGroups = it.selectedGroups + keys.filter { key -> key in sourceGroupKeys }) }
    }
    fun clearGroupSelection() { if (!state.value.saving) state.update { it.copy(selectedGroups = emptySet()) } }
    fun moveSelectedGroups(folder: String?): Boolean {
        val before = state.value
        moveGroups(before.selectedGroups, folder)
        val accepted = state.value.value !== before.value && state.value.issue == null
        if (accepted) clearGroupSelection()
        return accepted
    }
    fun moveChannel(id: String, folder: String?) = edit {
        require(source.any { it.id == id }); require(folder == null || folders.any { it.id == folder })
        copy(channelOverrides = channelOverrides.toMutableMap().apply { if (folder == null) remove(id) else put(id, folder) })
    }
    fun folder(id: String?, name: String, parent: String?) = edit {
        FolderEdits.upsert(this, id ?: "custom-${UUID.randomUUID()}", name, parent)
    }
    fun reorder(id: String, direction: Int) = edit { FolderEdits.move(this, id, direction) }
    fun reset() {
        edit { OrganizationConfig(sourceKey, enabled, revision = revision, folderSort = folderSort) }
        clearGroupSelection()
    }
    private fun edit(change: OrganizationConfig.() -> OrganizationConfig) {
        val current = state.value
        if (current.loading || current.saving || current.value == null || current.issue == OrganizationIssue.READ) return
        val updated = try { FolderEdits.validate(current.value.change()) } catch (_: IllegalArgumentException) {
            state.update { it.copy(issue = OrganizationIssue.INVALID_FOLDER) }; return
        }
        previewJob?.cancel()
        state.update { it.copy(value = updated, computing = true, dirty = true, saved = false, issue = null) }
        previewJob = viewModelScope.launch {
            val preview = withContext(background) { ChannelOrganizer.project(source, updated.copy(enabled = true)) }
            state.update { it.copy(value = updated.copy(folders = preview.tree.folders), preview = preview, computing = false) }
        }
    }
    fun save() {
        val current = state.value
        if (current.loading || current.computing || current.saving || current.value == null || current.issue != null) return
        state.update { it.copy(saving = true, issue = null) }
        viewModelScope.launch {
            try { repository.save(current.value); state.update { it.copy(saving = false, saved = true, dirty = false) } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { state.update { it.copy(saving = false, issue = OrganizationIssue.SAVE) } }
        }
    }
}