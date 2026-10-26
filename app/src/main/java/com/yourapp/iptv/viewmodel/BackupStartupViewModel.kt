package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@HiltViewModel
class BackupStartupViewModel @Inject constructor(private val repository: BackupRepository) : ViewModel() {
    val state = repository.maintenance
    init { retry() }
    fun retry() { viewModelScope.launch {
        try { repository.recover() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { }
    } }
}