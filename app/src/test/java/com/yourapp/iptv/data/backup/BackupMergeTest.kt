package com.yourapp.iptv.data.backup

import androidx.datastore.preferences.core.*
import com.yourapp.iptv.data.datastore.*
import com.yourapp.iptv.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class BackupMergeTest {
    @Test fun duplicatesKeepTheirCurrentNamesAndIdsWhileImportedIdCollisionsAreRemapped() {
        val current = backupAccounts("A")
        val saved = backupAccounts("B")
        val merged = BackupMerge.accounts(current, saved, true)
        assertEquals(2, merged.value.entries.size); assertEquals(1, merged.newCount)
        assertNotEquals("default", merged.value.activeId)
        assertEquals("B", merged.value.active.settings.xtreamUsername)
        val duplicate = BackupMerge.accounts(merged.value, current.copy(entries = current.entries.map { it.copy(name = "Old name") }), true)
        assertEquals(0, duplicate.newCount); assertEquals("A", duplicate.value.active.name)
        assertEquals(2, duplicate.value.entries.size)
    }
    @Test fun theEmptyInitialAccountDoesNotConsumeOneOfTheImportedAccountSlots() {
        val empty = Subscriptions(listOf(Subscription("default", "", AppSettings())), "default", null)
        val merged = BackupMerge.accounts(empty, backupAccounts("B"), true)
        assertEquals(1, merged.value.entries.size); assertEquals("B", merged.value.active.name)
    }
    @Test fun anIncompleteLegacyAccountWithAlternateCredentialsIsNotMistakenForAnEmptyPlaceholder() {
        val partial = AppSettings(xtreamServer = "https://synthetic.invalid",xtreamUsername = "Legacy",xtreamPassword = "keep")
        val current = Subscriptions(listOf(Subscription("default","",partial)),"default",null)
        val merged = BackupMerge.accounts(current,backupAccounts("B"),true)
        assertEquals(2,merged.value.entries.size)
        assertEquals(partial,merged.value.entries.first { it.id == "default" }.settings)
    }
    @Test fun incompleteSavedAccountsAlsoSurviveImportAndRepeatedRestoration() {
        val partial = AppSettings(m3uUrl = "",xtreamUsername = "Saved username")
        val saved = Subscriptions(listOf(Subscription("default","Incomplete",partial)),"default",null)
        val first = BackupMerge.accounts(backupAccounts("A"),saved,true)
        assertEquals(2,first.value.entries.size);assertEquals(partial,first.value.active.settings)
        assertEquals(2,BackupMerge.accounts(first.value,saved,true).value.entries.size)
    }
    @Test fun theSubscriptionLimitIsEnforcedBeforeAnyMutation() {
        val current = backupAccounts().copy(entries = (1..16).map { i ->
            Subscription(if (i == 1) "default" else "00000000-0000-0000-0000-${i.toString().padStart(12,'0')}", "A$i", backupAccount("A$i"))
        })
        assertEquals(BackupProblem.ACCOUNT_LIMIT, (runCatching { BackupMerge.accounts(current, backupAccounts("B"), true) }.exceptionOrNull() as BackupException).problem)
        assertEquals(16, current.entries.size)
    }
    @Test fun mergingHistoryAndProgressKeepsTheNewestEntryAndPreservesUnknownLocalPreferences() {
        val payload = backupPayload()
        val key = PlaylistCacheKey.of(payload.subscriptions.active.settings)
        val history = stringPreferencesKey("history_$key")
        val vod = stringPreferencesKey("vod_$key")
        val local = BackupStore.entries.associateWith { mutablePreferencesOf() }
        local.getValue(BackupStore.HOME)[stringPreferencesKey("future_local")] = "preserve"
        local.getValue(BackupStore.HISTORY)[history] = WatchHistoryCodec.encode(listOf(WatchHistoryEntry("42", 200)), 200)
        local.getValue(BackupStore.VOD)[vod] = VodProgressCodec.encode(listOf(VodProgressEntry(VodKind.MOVIE,"42","New","mp4",80_000,200_000,200)))
        val savedHistory = BackupValue(BackupValueType.STRING,
            WatchHistoryCodec.encode(listOf(WatchHistoryEntry("42",100),WatchHistoryEntry("43",90)),100))
        val savedProgress = BackupValue(BackupValueType.STRING,
            VodProgressCodec.encode(listOf(VodProgressEntry(VodKind.MOVIE,"42","Old","mp4",20_000,200_000,100))))
        val areas = payload.areas + mapOf(BackupStore.HISTORY to mapOf(history.name to savedHistory),
            BackupStore.VOD to mapOf(vod.name to savedProgress))
        val result = BackupMerge.stores(local, payload.copy(areas = areas), BackupRestoreOptions())
        assertEquals(200L, WatchHistoryCodec.decode(result.getValue(BackupStore.HISTORY)[history]).first().watchedAtMillis)
        assertEquals(80_000L, VodProgressCodec.decode(result.getValue(BackupStore.VOD)[vod]).single().positionMs)
        assertEquals("preserve", result.getValue(BackupStore.HOME)[stringPreferencesKey("future_local")])
    }
    @Test fun disablingSettingsRestoreKeepsLocalAppearanceAndTheCurrentActiveAccount() {
        val current = BackupStore.entries.associateWith { mutablePreferencesOf() }
        HomeAppearanceCodec.encode(current.getValue(BackupStore.HOME), HomeAppearance(theme = HomeTheme.RED))
        val result = BackupMerge.stores(current, backupPayload(), BackupRestoreOptions(settings = false))
        assertEquals(HomeTheme.RED, HomeAppearanceCodec.decode(result.getValue(BackupStore.HOME)).theme)
        assertEquals("A", BackupMerge.accounts(backupAccounts("A"), backupAccounts("B"), false).value.active.name)
    }
}