package com.yourapp.iptv.data.profile

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.yourapp.iptv.domain.profile.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfilePublicationDurabilityTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun aPublishedCommitSurvivesClosingAndReopeningARealPreferencesDataStore() = runBlocking {
        val file = temporary.root.resolve("people.preferences_pb")
        var expected = UserProfiles()
        repeat(2) { attempt ->
            val owner = SupervisorJob()
            val disk = PreferenceDataStoreFactory.create(scope = CoroutineScope(owner + Dispatchers.IO)) { file }
            try {
                ProfilePublicationFixture(disk).use { f ->
                    f.start()
                    if (attempt == 0) {
                        val pause = f.pauseObserver()
                        val writing = async(Dispatchers.Default) { f.profiles.rename("default","Persisted family") }
                        try {
                            assertEquals(ProfileResult.OK,withTimeout(3_000) { writing.await() })
                            expected = f.profiles.state.value.catalog
                        } finally { pause.release();writing.join() }
                    } else assertEquals(expected,f.profiles.state.value.catalog)
                }
            } finally { owner.cancelAndJoin() }
        }
    }
}