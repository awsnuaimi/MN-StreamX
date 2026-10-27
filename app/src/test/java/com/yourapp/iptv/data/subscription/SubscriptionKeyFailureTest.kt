package com.yourapp.iptv.data.subscription

import androidx.datastore.preferences.core.*
import com.yourapp.iptv.data.datastore.SettingsDataStore
import com.yourapp.iptv.data.profile.ProfileMemory
import com.yourapp.iptv.data.security.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.security.*
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SubscriptionKeyFailureTest {
    private val recordKey = stringPreferencesKey("subscriptions_encrypted_v1")
    private val settings = AppSettings(m3uUrl = "https://synthetic.invalid/list.m3u")
    @Test fun missingKeyCannotOverwriteAccountsThroughReadOrSave() = runBlocking {
        val keys = FaultKeyBackend();val disk = ProfileMemory()
        val repository = SettingsDataStore(disk,KeystoreSubscriptionCipher(keys.access()))
        repository.saveSubscription(Subscriptions.DEFAULT_ID,"Home",settings)
        val before = disk.values.value;keys.value = null
        val read = runCatching { repository.observeSubscriptions().first() }.exceptionOrNull()
        val write = runCatching { repository.saveSubscription(null,"Other",settings.copy(m3uUrl = settings.m3uUrl + "2")) }.exceptionOrNull()
        assertEquals(SecureStorageFailure.KEY_MISSING,(read as SecureStorageException).problem)
        assertEquals(SecureStorageFailure.KEY_MISSING,(write as SecureStorageException).problem)
        assertEquals(before,disk.values.value);assertEquals(0,keys.creates)
    }
    @Test fun anOutageDoesNotEraseAccountsAndRetryReadsTheOriginalCredentials() = runBlocking {
        val keys = FaultKeyBackend();val disk = ProfileMemory()
        val repository = SettingsDataStore(disk,KeystoreSubscriptionCipher(keys.access()))
        repository.saveSubscription(Subscriptions.DEFAULT_ID,"Home",settings)
        val before = disk.values.value;keys.failure = IOException("Synthetic outage")
        val error = runCatching { repository.observeSettings().first() }.exceptionOrNull() as SecureStorageException
        assertEquals(SecureStorageFailure.TEMPORARILY_UNAVAILABLE,error.problem);assertEquals(before,disk.values.value)
        keys.failure = null
        assertEquals(settings,repository.observeSettings().first());assertEquals(before,disk.values.value)
        assertEquals(0,keys.creates)
    }
    @Test fun legacyMigrationIsTheOnlyPathThatMayGenerateTheInitialSubscriptionKey() = runBlocking {
        val keys = FaultKeyBackend(null)
        val legacy = stringPreferencesKey("m3u_url")
        val disk = ProfileMemory(mutablePreferencesOf(legacy to settings.m3uUrl))
        val repository = SettingsDataStore(disk,KeystoreSubscriptionCipher(keys.access()))
        assertEquals(settings,repository.observeSettings().first())
        assertEquals(1,keys.creates);assertNull(disk.values.value[legacy]);assertNotNull(disk.values.value[recordKey])
        repository.observeSettings().first();assertEquals(1,keys.creates)
    }
    @Test fun anAuthenticatedMalformedCatalogIsNotReportedAsAKeyProblem() = runBlocking {
        val keys = FaultKeyBackend();val cipher = KeystoreSubscriptionCipher(keys.access())
        val record = cipher.encrypt("""{"version":99}""")
        val disk = ProfileMemory(mutablePreferencesOf(recordKey to record))
        val error = runCatching { SettingsDataStore(disk,cipher).observeSettings().first() }.exceptionOrNull() as SecureStorageException
        assertEquals(SecureStorageFailure.INVALID_RECORD,error.problem)
        assertEquals(record,disk.values.value[recordKey]);assertEquals(0,keys.creates)
    }
}