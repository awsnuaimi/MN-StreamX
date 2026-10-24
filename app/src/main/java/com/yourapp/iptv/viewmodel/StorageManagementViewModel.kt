package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.data.caption.CaptionStorageService
import com.yourapp.iptv.data.caption.CaptionStorageSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StorageManagementState(
    val snapshot: CaptionStorageSnapshot? = null,
    val busy: Boolean = false,
    val failure: Boolean = false,
    val blocked: Boolean = false,
    val deletedBytes: Long? = null
)

@HiltViewModel
class StorageManagementViewModel @Inject constructor(
    private val storage: CaptionStorageService
) : ViewModel() {
    private val mutable = MutableStateFlow(StorageManagementState())
    val state = mutable.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, failure = false, blocked = false)
        viewModelScope.launch {
            try { mutable.value = mutable.value.copy(snapshot = storage.snapshot(), busy = false) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.value = mutable.value.copy(busy = false, failure = true) }
        }
    }

    fun deleteModels() {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, failure = false, blocked = false, deletedBytes = null)
        viewModelScope.launch {
            try {
                val deleted = storage.deleteDownloadedModels()
                mutable.value = mutable.value.copy(
                    snapshot = storage.snapshot(), busy = false,
                    blocked = deleted == null, deletedBytes = deleted
                )
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.value = mutable.value.copy(busy = false, failure = true) }
        }
    }
}