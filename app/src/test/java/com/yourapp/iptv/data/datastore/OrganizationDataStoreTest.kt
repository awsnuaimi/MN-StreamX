package com.yourapp.iptv.data.datastore

import androidx.datastore.preferences.core.*
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.viewmodel.OrganizationSettingsFixture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class OrganizationDataStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun manualRulesAndHierarchySurviveReopeningWithoutTouchingOtherKeys() = runBlocking {
        val file = temporary.root.resolve("organization.preferences_pb")
        val settings = OrganizationSettingsFixture()
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val custom = ChannelFolder("custom-family", name = "Family", order = 10)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            store.edit { it[stringPreferencesKey("future")] = "keep" }
            val repo = OrganizationDataStore(store, settings)
            val original = repo.observe().first()
            repo.save(original.copy(enabled = true, folderSort = FolderSort.COUNT, folders = original.folders + custom,
                groupOverrides = mapOf(OrganizationNames.groupKey("SYRIA") to custom.id),
                channelOverrides = mapOf("channel-1" to "arabic-egypt")))
            assertEquals("keep", store.data.first()[stringPreferencesKey("future")])
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = OrganizationDataStore(PreferenceDataStoreFactory.create(scope = scope) { file }, settings)
            val value = repo.observe().first()
            assertTrue(value.enabled); assertEquals(1L, value.revision); assertEquals(FolderSort.COUNT, value.folderSort)
            assertTrue(value.folders.contains(custom))
            assertEquals(custom.id, value.groupOverrides[OrganizationNames.groupKey("SYRIA")])
            assertEquals("arabic-egypt", value.channelOverrides["channel-1"])
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
    @Test fun sourcesAreIsolatedAndStaleEditorsCannotOverwriteNewerRules() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val settings = OrganizationSettingsFixture()
            val repo = OrganizationDataStore(PreferenceDataStoreFactory.create(scope = scope) {
                temporary.root.resolve("scopes.preferences_pb")
            }, settings)
            val first = repo.observe().first()
            repo.save(first.copy(enabled = true, channelOverrides = mapOf("one" to "arabic-syria")))
            assertThrows(IOException::class.java) { runBlocking { repo.save(first) } }
            settings.value.value = settings.value.value.copy(m3uUrl = "https://other.test/list.m3u")
            val second = repo.observe().first()
            assertNotEquals(first.sourceKey, second.sourceKey)
            assertFalse(second.enabled); assertTrue(second.channelOverrides.isEmpty())
            assertThrows(IOException::class.java) { runBlocking { repo.save(first.copy(revision = 1)) } }
            repo.save(second.copy(enabled = true))
            settings.value.value = settings.value.value.copy(m3uUrl = "https://unit.test/list.m3u")
            assertEquals("arabic-syria", repo.observe().first().channelOverrides["one"])
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
    @Test fun malformedAndFutureConfigurationsAreNeverSilentlyOverwritten() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val settings = OrganizationSettingsFixture()
            val store = PreferenceDataStoreFactory.create(scope = scope) { temporary.root.resolve("corrupt.preferences_pb") }
            val repo = OrganizationDataStore(store, settings)
            val source = OrganizationNames.sourceKey(settings.value.value)
            val key = stringPreferencesKey("source_$source")
            val valid = OrganizationCodec.encode(OrganizationConfig(sourceKey = source))
            for (bad in listOf("{invalid", valid.replace("\"version\":1", "\"version\":99"))) {
                store.edit { it[key] = bad }
                assertThrows(IOException::class.java) { runBlocking { repo.observe().first() } }
                assertThrows(IOException::class.java) { runBlocking { repo.save(OrganizationConfig(sourceKey = source)) } }
                assertEquals(bad, store.data.first()[key])
            }
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
}