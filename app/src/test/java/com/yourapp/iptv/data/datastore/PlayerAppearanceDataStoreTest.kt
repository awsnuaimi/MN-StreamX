package com.yourapp.iptv.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yourapp.iptv.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlayerAppearanceDataStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun savedAppearanceSurvivesReopeningStorage() = runBlocking {
        val file = temporary.root.resolve("appearance.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val expected = PlayerAppearance(style = PlayerInfoStyle.CLASSIC, displaySeconds = 8, showHints = true)
        try {
            val first = PlayerAppearanceDataStore(PreferenceDataStoreFactory.create(scope = firstScope) { file })
            assertEquals(PlayerAppearance(), first.observe().first())
            first.save(expected)
            assertEquals(expected, first.observe().first())
        } finally { firstScope.coroutineContext[Job]!!.cancelAndJoin() }
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = PlayerAppearanceDataStore(PreferenceDataStoreFactory.create(scope = secondScope) { file })
            assertEquals(expected, reopened.observe().first())
        } finally { secondScope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun savingAndResettingAppearanceCannotModifySubscriptionStorage() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val accountFile = temporary.root.resolve("settings.preferences_pb")
        try {
            val account = PreferenceDataStoreFactory.create(scope = scope) { accountFile }
            account.edit {
                it[stringPreferencesKey("xtream_server")] = "https://unit.test"
                it[stringPreferencesKey("xtream_password")] = "test-only-password"
            }
            val before = accountFile.readBytes()
            val appearance = PlayerAppearanceDataStore(PreferenceDataStoreFactory.create(scope = scope) {
                temporary.root.resolve("appearance.preferences_pb")
            })
            appearance.save(PlayerAppearance(textSize = PlayerTextSize.LARGE))
            appearance.save(PlayerAppearance())
            assertArrayEquals(before, accountFile.readBytes())
            assertEquals("test-only-password", account.data.first()[stringPreferencesKey("xtream_password")])
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
}