package com.yourapp.iptv.data.datastore

import androidx.datastore.preferences.core.*
import com.yourapp.iptv.data.backup.AppDataGate
import com.yourapp.iptv.data.profile.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class LiveTvPreferencesDataStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun sortHidingAndQualitySurviveClosingAndReopeningTheActualFile() = runBlocking<Unit> {
        val file = temporary.root.resolve("live.preferences_pb")
        val account = ProfileSettings(); val people = ProfilePeople()
        val key = ProfileScope.of(account.values.value)
        val saved = LiveTvPreferences(LiveTvSort.NAME, setOf("one"), setOf("PL| NEWS"), setOf("arabic"), mapOf("f".repeat(64) to "hd"))
        var owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = owner) { file }
            store.edit { it[stringPreferencesKey("source_legacy")] = "untouched" }
            val repo = LiveTvPreferencesDataStore(store, { account.values.value }, people)
            repo.update(key) { saved }
            assertEquals(saved, repo.observe(key).first())
        } finally { owner.coroutineContext[Job]!!.cancelAndJoin() }
        owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = owner) { file }
            assertEquals(saved, LiveTvPreferencesDataStore(store, { account.values.value }, people).observe(key).first())
            assertEquals("untouched", store.data.first()[stringPreferencesKey("source_legacy")])
        } finally { owner.coroutineContext[Job]!!.cancelAndJoin() }
    }
    @Test fun identicalChannelIdsAreIndependentAcrossProfilesAndSources() = runBlocking<Unit> {
        val disk = ProfileMemory(); val account = ProfileSettings()
        val people = ProfilePeople(UserProfiles(UserProfiles().entries + profileRow()))
        val repo = LiveTvPreferencesDataStore(disk, { account.values.value }, people)
        val first = ProfileScope.of(account.values.value)
        repo.update(first) { it.copy(hiddenChannels = setOf("same")) }
        people.state.value = people.state.value.copy(catalog = people.state.value.catalog.copy(activeId = PROFILE_A))
        account.values.value = account.values.value.copy(profileId = PROFILE_A)
        val personal = ProfileScope.of(account.values.value)
        assertTrue(repo.observe(personal).first().hiddenChannels.isEmpty())
        repo.update(personal) { it.copy(sort = LiveTvSort.FAVORITES) }
        account.values.value = account.values.value.copy(m3uUrl = "https://synthetic.invalid/another")
        assertEquals(LiveTvPreferences(), repo.observe(ProfileScope.of(account.values.value)).first())
        assertEquals(setOf("same"), repo.observe(first).first().hiddenChannels)
        assertEquals(LiveTvSort.FAVORITES, repo.observe(personal).first().sort)
    }
    @Test fun aWriterQueuedBeforeASourceSwitchIsRejectedInsideTheSharedGate() = runBlocking<Unit> {
        val disk = ProfileMemory(); val account = ProfileSettings(); val people = ProfilePeople()
        val repo = LiveTvPreferencesDataStore(disk, { account.values.value }, people); val key = ProfileScope.of(account.values.value)
        lateinit var job: Deferred<Boolean>
        AppDataGate.write {
            job = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repo.update(key) { it.copy(sort = LiveTvSort.NAME) } }.isFailure }
            account.values.value = account.values.value.copy(m3uUrl = "https://synthetic.invalid/switched")
        }
        assertTrue(job.await()); assertTrue(disk.values.value.asMap().isEmpty())
    }
    @Test fun switchingRetiredAndFailedProfilesCannotWriteAnEarlierNamespace() = runBlocking<Unit> {
        val disk = ProfileMemory(); val account = ProfileSettings(); val people = ProfilePeople()
        val repo = LiveTvPreferencesDataStore(disk, { account.values.value }, people); val key = ProfileScope.of(account.values.value)
        for (state in listOf(people.state.value.copy(switching = true), people.state.value.copy(failed = true),
            people.state.value.copy(ready = false), people.state.value.copy(catalog = UserProfiles(UserProfiles().entries + profileRow(), PROFILE_A)))) {
            people.state.value = state
            assertThrows(IOException::class.java) { runBlocking { repo.update(key) { it.copy(sort = LiveTvSort.NAME) } } }
        }
        assertTrue(disk.values.value.asMap().isEmpty())
    }
    @Test fun concurrentTransformsPreserveBothEditsAndDiskFailurePreservesTheOldValue() = runBlocking<Unit> {
        val disk = ProfileMemory(); val account = ProfileSettings(); val repo = LiveTvPreferencesDataStore(disk, { account.values.value }, ProfilePeople())
        val key = ProfileScope.of(account.values.value)
        coroutineScope {
            launch(Dispatchers.Default) { repo.update(key) { it.copy(hiddenChannels = it.hiddenChannels + "one") } }
            launch(Dispatchers.Default) { repo.update(key) { it.copy(sort = LiveTvSort.NAME) } }
        }
        val before = repo.observe(key).first(); assertEquals(setOf("one"), before.hiddenChannels); assertEquals(LiveTvSort.NAME, before.sort)
        disk.fail = true
        assertThrows(IOException::class.java) { runBlocking { repo.update(key) { it.showAll() } } }
        assertEquals(before, repo.observe(key).first())
    }
    @Test fun malformedAndFutureRecordsCannotBeSilentlyReplacedByADefault() = runBlocking<Unit> {
        val disk = ProfileMemory(); val account = ProfileSettings(); val repo = LiveTvPreferencesDataStore(disk, { account.values.value }, ProfilePeople())
        val key = ProfileScope.of(account.values.value); val pref = stringPreferencesKey("live_$key")
        for (bad in listOf("{oops", LiveTvPreferencesCodec.encode(LiveTvPreferences()).replace("\"version\":1", "\"version\":99"))) {
            disk.values.value = mutablePreferencesOf(pref to bad)
            assertThrows(IOException::class.java) { runBlocking { repo.observe(key).first() } }
            assertThrows(IOException::class.java) { runBlocking { repo.update(key) { LiveTvPreferences() } } }
            assertEquals(bad, disk.values.value[pref])
        }
    }
    @Test fun productionSettingsReadDoesNotReenterTheApplicationGate() = runBlocking<Unit> {
        val cipher = com.yourapp.iptv.data.subscription.KeystoreSubscriptionCipher {
            javax.crypto.spec.SecretKeySpec(ByteArray(32) { 1 }, "AES")
        }
        val settings = SettingsDataStore(ProfileMemory(), cipher)
        settings.saveSettings(AppSettings(m3uUrl = "https://synthetic.invalid/live"))
        val key = ProfileScope.of(settings.observeSettings().first())
        val disk = ProfileMemory()
        val repo = LiveTvPreferencesDataStore(disk, settings::committedSettings, ProfilePeople())
        withTimeout(2_000) { repo.update(key) { it.copy(sort = LiveTvSort.NAME) } }
        assertEquals(LiveTvSort.NAME, repo.observe(key).first().sort)
        settings.saveSettings(AppSettings(m3uUrl = "https://synthetic.invalid/new"))
        assertThrows(IOException::class.java) { runBlocking { repo.update(key) { it.showAll() } } }
    }

}