package com.yourapp.iptv.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChannelSnapshotDataStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun provenanceSurvivesReopeningAndInvalidationIsPersistent() = runBlocking {
        val file = temporary.root.resolve("snapshot.preferences_pb")
        suspend fun openAndRun(block: suspend (ChannelSnapshotDataStore) -> Unit) {
            val owner = SupervisorJob()
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(owner + Dispatchers.IO)) { file }
            try { block(ChannelSnapshotDataStore(store)) } finally { owner.cancelAndJoin() }
        }
        openAndRun { assertNull(it.read()); it.write("account-fingerprint") }
        openAndRun { assertEquals("account-fingerprint", it.read()); it.write(null) }
        openAndRun { assertNull(it.read()) }
    }
}