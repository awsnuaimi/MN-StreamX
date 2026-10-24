package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.di.CaptionWorker
import com.yourapp.iptv.domain.caption.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import com.yourapp.iptv.ui.state.CaptionUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import java.util.Locale

@HiltViewModel
class CaptionViewModel @Inject constructor(private val repository: CaptionRepository,
    @CaptionWorker worker: CoroutineDispatcher, models: CaptionModels, speech: SpeechEngineFactory,
    translators: CaptionTranslatorFactory, private val device: CaptionDevice,
    private val vault: CloudCredentialVault, cloudFactory: CloudCaptionSessionFactory,
    storageCoordinator: CaptionStorageCoordinator = CaptionStorageCoordinator()) : ViewModel() {
    private val state = MutableStateFlow(CaptionUiState())
    val uiState = state.asStateFlow()
    private val pipeline = CaptionPipeline(viewModelScope, worker, models, speech, translators, device, vault, cloudFactory,
        storageCoordinator = storageCoordinator)
    val generated = pipeline.uiState
    private var observe: Job? = null
    private var write: Job? = null
    private var input: CaptionAudioInput? = null
    private var inputKey: String? = null
    private data class ActiveKey(val input: CaptionAudioInput, val media: String, val source: String,
        val target: String, val backend: TranslationBackend, val credentialsRevision: Int)
    private var activeKey: ActiveKey? = null
    private var credentialsRevision = 0

    init {
        reload()
        val revisionAtStart = credentialsRevision
        viewModelScope.launch {
            val saved = try { vault.read() }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { null }
            if (revisionAtStart == credentialsRevision) {
                state.value = state.value.copy(cloudConfigured = saved != null,
                    cloudRegion = saved?.region.orEmpty())
            }
        }
    }
    fun saveCloudCredentials(key: String, region: String) {
        if (state.value.cloudSaving) return
        val credential = CloudCredentials(key.trim(), region.trim().lowercase(Locale.ROOT))
        if (!credential.valid()) { state.value = state.value.copy(cloudSaveFailed = true); return }
        state.value = state.value.copy(cloudSaving = true, cloudSaveFailed = false)
        viewModelScope.launch {
            try {
                vault.save(credential)
                credentialsRevision++
                state.value = state.value.copy(cloudSaving = false, cloudSaveFailed = false,
                    cloudConfigured = true, cloudRegion = credential.region)
                reconcile()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { state.value = state.value.copy(cloudSaving = false, cloudSaveFailed = true) }
        }
    }
    fun deleteCloudCredentials() {
        if (state.value.cloudSaving) return
        state.value = state.value.copy(cloudSaving = true, cloudSaveFailed = false)
        viewModelScope.launch {
            try {
                vault.clear()
                credentialsRevision++
                state.value = state.value.copy(cloudSaving = false, cloudConfigured = false, cloudRegion = "")
                reconcile()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { state.value = state.value.copy(cloudSaving = false, cloudSaveFailed = true) }
        }
    }
    fun reload() {
        if (observe?.isActive == true) return
        state.value = state.value.copy(readFailed = false)
        observe = viewModelScope.launch {
            try { repository.observe().collect { value ->
                state.value = state.value.copy(settings = value.normalized(), loaded = true, readFailed = false)
                reconcile()
            } } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                pipeline.stop(); activeKey = null
                state.value = state.value.copy(loaded = false, readFailed = true)
            }
        }
    }
    fun save(value: CaptionSettings) {
        if (!state.value.loaded || state.value.saving || write?.isActive == true) return
        state.value = state.value.copy(saving = true, saveFailed = false)
        write = viewModelScope.launch {
            try { repository.save(value.normalized()); state.value = state.value.copy(saving = false) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { state.value = state.value.copy(saving = false, saveFailed = true) }
        }
    }
    fun bind(source: CaptionAudioInput?, key: String?) {
        if (source === input && key == inputKey) return
        input = source; inputKey = key
        reconcile()
    }
    fun retry() { activeKey = null; reconcile() }
    fun deviceStats(): CaptionDeviceStats? = device.snapshot()
    private fun reconcile() {
        val settings = state.value.settings
        val current = input
        val key = if (state.value.loaded && settings.mode == CaptionMode.GENERATED && current != null && inputKey != null)
            ActiveKey(current, inputKey!!, settings.source, settings.target.tag,
                settings.translationBackend, credentialsRevision) else null
        if (key == activeKey) return
        activeKey = key
        if (key == null) pipeline.stop() else pipeline.start(current!!, settings)
    }
    override fun onCleared() { pipeline.stop(); super.onCleared() }
}