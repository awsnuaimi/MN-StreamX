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
    val issue: OrganizationIssue? = null)

@HiltViewModel
class OrganizationEditorViewModel @Inject constructor(private val repository: OrganizationRepository,
    private val channels: ChannelRepository, @DefaultDispatcher private val background: CoroutineDispatcher) : ViewModel() {
    private val state = MutableStateFlow(OrganizationEditorState())
    val uiState = state.asStateFlow()
    private var source = emptyList<Channel>()
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
                state.value = OrganizationEditorState(config.copy(folders = preview.tree.folders), preview, loading = false)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { state.value = OrganizationEditorState(loading = false, issue = OrganizationIssue.READ) }
        }
    }
    fun enable(value: Boolean) = edit { copy(enabled = value) }
    fun moveGroup(key: String, folder: String?) = edit {
        require(folder == null || folders.any { it.id == folder })
        copy(groupOverrides = groupOverrides.toMutableMap().apply { if (folder == null) remove(key) else put(key, folder) })
    }
    fun moveChannel(id: String, folder: String?) = edit {
        require(source.any { it.id == id }); require(folder == null || folders.any { it.id == folder })
        copy(channelOverrides = channelOverrides.toMutableMap().apply { if (folder == null) remove(id) else put(id, folder) })
    }
    fun folder(id: String?, name: String, parent: String?) = edit {
        FolderEdits.upsert(this, id ?: "custom-${UUID.randomUUID()}", name, parent)
    }
    fun reorder(id: String, direction: Int) = edit { FolderEdits.move(this, id, direction) }
    fun reset() = edit { OrganizationConfig(sourceKey, enabled, revision = revision) }
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