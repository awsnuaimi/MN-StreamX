package com.yourapp.iptv.data.backup

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*

/** Normal writers and restore transactions share one lock. Never call a repository inside this lock. */
internal object AppDataGate {
    private val mutex = Mutex()
    suspend fun <T> write(block: suspend () -> T): T = mutex.withLock { block() }
}

internal suspend fun DataStore<Preferences>.editAppData(transform: suspend (MutablePreferences) -> Unit): Preferences =
    AppDataGate.write { edit(transform) }