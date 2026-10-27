package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.*
import com.yourapp.iptv.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ParentalEditorState(val sourceKey: String? = null, val groups: List<String> = emptyList(),
    val loading: Boolean = false, val groupError: Boolean = false,
    val busy: Boolean = false, val result: ParentalResult? = null)
data class ParentalExternalFlow(val token: Long, val awaitingResult: Boolean = true)

@HiltViewModel
class ParentalViewModel @Inject constructor(val control: ParentalControl, settings: SettingsRepository,
    private val catalog: ParentalCatalog) : ViewModel() {
    val access = control.state
    private val mutable = MutableStateFlow(ParentalEditorState())
    val editor = mutable.asStateFlow()
    private val pin = MutableStateFlow<String?>(null)
    val pinRequest = pin.asStateFlow()
    private val route = MutableStateFlow<String?>(null)
    val pendingRoute = route.asStateFlow()
    private val external = MutableStateFlow<ParentalExternalFlow?>(null)
    val externalFlow = external.asStateFlow()
    private val reset = MutableStateFlow(0L)
    val renderReset = reset.asStateFlow()
    private var leaseCounter = 0L
    private var leaseTimeout: Job? = null
    private var account: AppSettings? = null
    private var groupsJob: Job? = null
    private var operation: Job? = null
    private var generation = 0L

    init {
        viewModelScope.launch {
            control.initialize()
            try {
                settings.observeSettings().distinctUntilChangedBy(PlaylistCacheKey::of).collect { value ->
                    if (account != null) lock()
                    account = value; generation++; groupsJob?.cancel()
                    mutable.value = ParentalEditorState(sourceKey = PlaylistCacheKey.of(value))
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { account = null; mutable.update { it.copy(groupError = true) } }
        }
    }
    fun retryStorage() { viewModelScope.launch { control.initialize() } }
    fun foreground() = control.foreground()
    fun background() { operation?.cancel(); dismissPin(); route.value = null; control.background() }
    fun lock() { operation?.cancel(); dismissPin(); route.value = null; cancelExternal() }
    /** Only called immediately before an explicit document/installer launch from an authorized screen. */
    fun beginExternal(existing: Long? = null): Long? {
        if (!access.value.configured) return null
        if (existing != null && external.value?.token == existing) return existing
        if (!access.value.unlocked || access.value.storageError) throw ParentalDenied()
        leaseTimeout?.cancel()
        val token = ++leaseCounter; external.value = ParentalExternalFlow(token)
        leaseTimeout = viewModelScope.launch { delay(10 * 60_000L); if (external.value?.token == token) cancelExternal() }
        return token
    }
    fun finishExternal(token: Long?) {
        if (token == null || token != external.value?.token) return
        external.value = external.value?.copy(awaitingResult = false)
    }
    fun consumeExternal(token: Long) {
        if (external.value?.token == token && external.value?.awaitingResult == false && access.value.unlocked) {
            leaseTimeout?.cancel(); external.value = null
        }
    }
    fun cancelExternal() {
        operation?.cancel(); dismissPin(); route.value = null
        leaseTimeout?.cancel(); external.value = null; reset.value++; control.lock()
    }
    fun requestPin(destination: String? = null) {
        mutable.update { it.copy(result = null) }; pin.value = destination ?: "home"
    }
    fun dismissPin() { pin.value = null; mutable.update { it.copy(result = null) } }
    fun routeHandled() { route.value = null }
    fun unlock(value: CharArray) {
        val destination = pin.value ?: run { value.fill('\u0000'); return }
        runOperation(value, destination) { control.unlock(value) }
    }
    fun create(value: CharArray) = runOperation(value,"parental") { control.create(value) }
    fun changePin(old: CharArray, replacement: CharArray?) {
        if (mutable.value.busy) { old.fill('\u0000'); replacement?.fill('\u0000'); return }
        runOperation(old,null) { try { control.changePin(old,replacement) } finally { replacement?.fill('\u0000') } }
    }
    fun saveRules(value: ParentalRules) = runOperation(null,null) { control.saveRules(value) }
    private fun runOperation(secret: CharArray?, destination: String?, action: suspend () -> ParentalResult) {
        if (mutable.value.busy) { secret?.fill('\u0000'); return }
        mutable.update { it.copy(busy = true,result = null) }
        operation = viewModelScope.launch {
            try {
                val result = action()
                mutable.update { it.copy(result = result) }
                if (result == ParentalResult.OK) {
                    pin.value = null
                    if (destination != null && destination != "home") route.value = destination
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.update { it.copy(result = ParentalResult.STORAGE_ERROR) } }
            finally { secret?.fill('\u0000'); mutable.update { it.copy(busy = false) } }
        }
    }
    fun loadGroups() {
        val source = account ?: return
        groupsJob?.cancel(); val request = ++generation; val epoch = access.value.epoch
        mutable.update { it.copy(groups = emptyList(),loading = true,groupError = false) }
        groupsJob = viewModelScope.launch {
            try {
                val groups = catalog.groups(source)
                if (request == generation && epoch == access.value.epoch)
                    mutable.update { it.copy(groups = groups,loading = false) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { if (request == generation) mutable.update { it.copy(loading = false,groupError = true) } }
        }
    }
}