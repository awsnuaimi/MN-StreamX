package com.yourapp.iptv.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.repository.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class HistoryPressureSettings : SettingsRepository {
    val value = MutableStateFlow(AppSettings(m3uUrl = "https://synthetic.invalid/history"))
    override fun observeSettings() = value
    override suspend fun saveSettings(settings: AppSettings) { value.value = settings }
}
internal class HistoryPressureStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    val values = MutableStateFlow(initial)
    override val data: Flow<Preferences> = values
    var gate: CompletableDeferred<Unit>? = null
    var fail = false
    var writes = 0
    private val mutex = Mutex()
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
        gate?.await();if (fail) throw IOException("Synthetic slow history storage")
        transform(values.value).also { values.value = it;writes++ }
    }
}