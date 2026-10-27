package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.profile.*
import com.yourapp.iptv.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ProfileOperation(val busy: Boolean = false, val result: ProfileResult? = null)
@HiltViewModel
class ProfilesViewModel @Inject constructor(private val repository: ProfileRepository) : ViewModel() {
    val people = repository.state
    private val mutable = MutableStateFlow(ProfileOperation())
    val operation = mutable.asStateFlow()
    init { viewModelScope.launch { repository.initialize() } }
    fun dismiss() { mutable.value = mutable.value.copy(result = null) }
    fun select(id: String, done: (ProfileResult) -> Unit) = run({ repository.select(id) },done)
    fun create(name: String, kind: ProfileKind) = run(action = { repository.create(name,kind) })
    fun rename(id: String, name: String) = run(action = { repository.rename(id,name) })
    fun remove(id: String) = run(action = { repository.remove(id) })
    private fun run(action: suspend () -> ProfileResult, done: (ProfileResult) -> Unit = {}) {
        if (mutable.value.busy) return
        mutable.value = ProfileOperation(busy = true)
        viewModelScope.launch {
            try { val result = action();mutable.value = ProfileOperation(result = result);done(result) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.value = ProfileOperation(result = ProfileResult.STORAGE) }
        }
    }
}