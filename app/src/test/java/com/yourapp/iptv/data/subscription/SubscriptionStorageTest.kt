package com.yourapp.iptv.data.subscription

import androidx.datastore.preferences.core.*
import com.yourapp.iptv.data.datastore.SettingsDataStore
import com.yourapp.iptv.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.spec.SecretKeySpec

class SubscriptionStorageTest {
    @get:Rule val temporary = TemporaryFolder()
    private val cipher = KeystoreSubscriptionCipher { SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES") }
    private val recordKey = stringPreferencesKey("subscriptions_encrypted_v1")
    private fun account(user: String) = AppSettings(source = PlaylistSource.XTREAM, xtreamServer = "https://synthetic.invalid",
        xtreamUsername = user, xtreamPassword = " synthetic & password ")
    private suspend fun withStore(block: suspend (androidx.datastore.core.DataStore<Preferences>) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = temporary.newFolder().resolve("accounts.preferences_pb")
        try { block(PreferenceDataStoreFactory.create(scope = scope) { file }) }
        finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun legacyAccountIsEncryptedWithoutChangingItsHistoryKeyOrUnrelatedPreferences() = runBlocking {
        withStore { disk ->
            val original = account("A")
            disk.edit {
                it[stringPreferencesKey("xtream_server")] = original.xtreamServer
                it[stringPreferencesKey("xtream_username")] = original.xtreamUsername
                it[stringPreferencesKey("xtream_password")] = original.xtreamPassword
                it[stringPreferencesKey("playlist_source")] = "XTREAM"
                it[stringPreferencesKey("future")] = "preserve"
            }
            val repository = SettingsDataStore(disk, cipher)
            val saved = repository.observeSubscriptions().first()
            assertEquals(original, saved.active.settings)
            assertEquals(PlaylistCacheKey.of(original), saved.legacySourceKey)
            val values = disk.data.first()
            assertNull(values[stringPreferencesKey("xtream_password")])
            assertNull(values[stringPreferencesKey("xtream_server")])
            assertEquals("preserve", values[stringPreferencesKey("future")])
            assertFalse(values[recordKey]!!.contains(original.xtreamPassword))
            assertFalse(values[recordKey]!!.contains(original.xtreamServer))
        }
    }

    @Test fun accountsPasswordsAndTheActiveChoiceSurviveActualFileReopening() = runBlocking {
        val file = temporary.root.resolve("reopen.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val a = account("A"); val b = account("B")
        lateinit var expected: Subscriptions
        try {
            val repository = SettingsDataStore(PreferenceDataStoreFactory.create(scope = scope) { file }, cipher)
            repository.saveSubscription(Subscriptions.DEFAULT_ID, "First", a)
            repository.saveSubscription(null, "Second", b)
            repository.activateSubscription(Subscriptions.DEFAULT_ID)
            expected = repository.observeSubscriptions().first()
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = SettingsDataStore(PreferenceDataStoreFactory.create(scope = scope) { file }, cipher)
            assertEquals(expected, repository.observeSubscriptions().first())
            assertEquals(a, repository.observeSettings().first())
            assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("synthetic.invalid"))
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun gcmUsesFreshIvsAndRejectsTamperingAndADifferentKey() {
        val first = cipher.encrypt("synthetic secret")
        assertNotEquals(first, cipher.encrypt("synthetic secret"))
        assertEquals("synthetic secret", cipher.decrypt(first))
        val bytes = java.util.Base64.getDecoder().decode(first.removePrefix("gcm1:")); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertTrue(runCatching { cipher.decrypt("gcm1:" + java.util.Base64.getEncoder().encodeToString(bytes)) }.isFailure)
        val other = KeystoreSubscriptionCipher { SecretKeySpec(ByteArray(32) { 99 }, "AES") }
        assertTrue(runCatching { other.decrypt(first) }.isFailure)
    }

    @Test fun failedEncryptionDoesNotRemoveLegacyCredentialsOrCommitAPartialMigration() = runBlocking {
        withStore { disk ->
            disk.edit { it[stringPreferencesKey("m3u_url")] = "https://synthetic.invalid/legacy.m3u" }
            val broken = object : SubscriptionCipher {
                override fun encrypt(value: String): String = error("Synthetic encryption failure")
                override fun decrypt(value: String): String = error("Not used")
            }
            assertTrue(runCatching { SettingsDataStore(disk, broken).observeSettings().first() }.isFailure)
            assertNotNull(disk.data.first()[stringPreferencesKey("m3u_url")]); assertNull(disk.data.first()[recordKey])
            assertEquals("https://synthetic.invalid/legacy.m3u", SettingsDataStore(disk, cipher).observeSettings().first().m3uUrl)
        }
    }

    @Test fun corruptSavedAccountsAreReportedWithoutOverwritingTheRecord() = runBlocking {
        withStore { disk ->
            val invalid = cipher.encrypt("""{"version":99}""")
            disk.edit { it[recordKey] = invalid }
            val repository = SettingsDataStore(disk, cipher)
            assertTrue(runCatching { repository.observeSettings().first() }.isFailure)
            assertTrue(runCatching { repository.saveSubscription(null, "New", account("B")) }.isFailure)
            assertEquals(invalid, disk.data.first()[recordKey])
        }
    }

    @Test fun duplicateAccountsAndDeletingTheActiveAccountCannotChangePersistedState() = runBlocking {
        withStore { disk ->
            val repository = SettingsDataStore(disk, cipher)
            repository.saveSubscription(Subscriptions.DEFAULT_ID, "Original", account("A"))
            val before = disk.data.first()[recordKey]
            assertTrue(runCatching { repository.saveSubscription(null, "Duplicate", account("A")) }.exceptionOrNull() is SubscriptionException)
            assertTrue(runCatching { repository.removeSubscription(Subscriptions.DEFAULT_ID) }.exceptionOrNull() is SubscriptionException)
            assertEquals(before, disk.data.first()[recordKey])
        }
    }
    @Test fun concurrentAddsDoNotLoseAccountsOrSeparateTheActiveIdFromItsCredentials() = runBlocking {
        withStore { disk ->
            val repository = SettingsDataStore(disk, cipher)
            repository.saveSubscription(Subscriptions.DEFAULT_ID, "Original", account("A"))
            coroutineScope {
                listOf(async { repository.saveSubscription(null, "Second", account("B")) },
                    async { repository.saveSubscription(null, "Third", account("C")) }).awaitAll()
            }
            val saved = repository.observeSubscriptions().first()
            assertEquals(setOf("A", "B", "C"), saved.entries.map { it.settings.xtreamUsername }.toSet())
            assertEquals(saved.active.settings, repository.observeSettings().first())
        }
    }

}