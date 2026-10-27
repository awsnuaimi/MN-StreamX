package com.yourapp.iptv.data.backup

import androidx.datastore.preferences.core.*
import com.google.gson.JsonParser
import com.yourapp.iptv.data.profile.*
import com.yourapp.iptv.data.datastore.WatchHistoryCodec
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.profile.*
import com.yourapp.iptv.domain.parental.*
import org.junit.Assert.*
import org.junit.Test

class ProfileBackupTest {
    @Test fun v1BackupsStillRestoreIntoTheOriginalProfileOnly() {
        val root = JsonParser.parseString(String(BackupPayloadCodec.encode(backupPayload()))).asJsonObject
        root.addProperty("version",1);root.remove("profiles")
        val decoded = BackupPayloadCodec.decode(root.toString().toByteArray())
        assertEquals(UserProfiles().entries,decoded.profiles)
    }
    @Test fun portableV2PreservesPersonalDataNamespacesButNeverExportsTheActiveProfileOrSecurityRules() {
        val basic = backupPayload();val source = PlaylistCacheKey.of(basic.subscriptions.active.settings)
        val people = UserProfiles().entries + profileRow(kind = ProfileKind.CHILD)
        val key = ProfileScope.key(PROFILE_A,source)
        val history = BackupValue(BackupValueType.STRING,WatchHistoryCodec.encode(listOf(WatchHistoryEntry("42",100)),100))
        val value = basic.copy(profiles = people,favorites = listOf(BackupFavorite(key,"42")),
            areas = basic.areas + (BackupStore.HISTORY to mapOf("history_$key" to history)))
        val encoded = BackupPayloadCodec.encode(value);val decoded = BackupPayloadCodec.decode(encoded)
        assertEquals(value.favorites,decoded.favorites);assertEquals(value.areas,decoded.areas)
        assertEquals(ParentalSection.entries.toSet(),decoded.profiles.last().rules.sections)
        val root = JsonParser.parseString(String(encoded)).asJsonObject
        assertFalse(root.has("active"));assertFalse(String(encoded).contains("rules"));assertFalse(String(encoded).contains("PIN"))
    }
    @Test fun unknownProfileNamespacesCannotBeInjectedByABackup() {
        val basic = backupPayload();val raw = PlaylistCacheKey.of(basic.subscriptions.active.settings)
        assertTrue(runCatching { BackupPayloadCodec.encode(basic.copy(favorites = listOf(BackupFavorite(ProfileScope.key(PROFILE_A,raw),"42")))) }.isFailure)
    }
    @Test fun restoringCannotPromoteAnExistingChildAndKeepsItsLocalNameRulesAndActiveSelection() {
        val local = profileRow(kind = ProfileKind.CHILD).copy(name = "Local",rules = ParentalRules(setOf(ParentalSection.SETTINGS)))
        val current = UserProfiles(UserProfiles().entries + local,PROFILE_A)
        val incoming = profileRow().copy(name = "Imported",home = HomeAppearance(language = HomeLanguage.GERMAN,profileId = PROFILE_A))
        val merged = BackupProfileMerge.merge(current,UserProfiles().entries + incoming,true)
        assertEquals(PROFILE_A,merged.activeId);assertEquals(ProfileKind.CHILD,merged.active.kind)
        assertEquals("Local",merged.active.name);assertEquals(local.rules,merged.active.rules)
        assertEquals(HomeLanguage.GERMAN,merged.active.home?.language)
    }
    @Test fun newImportedChildrenStartLockedAndAreNeverSelectedAutomatically() {
        val incoming = profileRow(kind = ProfileKind.CHILD).copy(rules = ParentalRules(emptySet()))
        val result = BackupProfileMerge.merge(UserProfiles(),UserProfiles().entries + incoming,true)
        assertEquals("default",result.activeId);assertEquals(ParentalSection.entries.toSet(),result.entries.last().rules.sections)
    }
    @Test fun settingsOptOutPreservesExistingAppearanceAndCapacityIsValidatedBeforeRestore() {
        val old = profileRow();val current = UserProfiles(UserProfiles().entries + old)
        val newer = old.copy(home = old.home!!.copy(language = HomeLanguage.GERMAN))
        assertEquals(old,BackupProfileMerge.merge(current,UserProfiles().entries + newer,false).entries.last())
        val full = UserProfiles(UserProfiles().entries + (1..7).map { index -> profileRow("00000000-0000-4000-8000-%012d".format(index)) })
        assertEquals(BackupProblem.PROFILE_LIMIT,(runCatching { BackupProfileMerge.merge(full,UserProfiles().entries + profileRow(),true) }.exceptionOrNull() as BackupException).problem)
    }
    @Test fun version1CrashJournalCanBeRecoveredAfterUpgradingToProfiles() {
        val before = BackupSnapshot(BackupStore.entries.associateWith { emptyPreferences() },emptyList(),emptyList())
        val root = JsonParser.parseString(String(BackupSnapshotCodec.encode(before))).asJsonObject
        root.addProperty("version",1);root.getAsJsonObject("stores").remove("PROFILES")
        val restored = BackupSnapshotCodec.decode(root.toString().toByteArray())
        assertEquals(before,restored)
    }
}