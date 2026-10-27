package com.yourapp.iptv.data.profile

import androidx.datastore.preferences.core.*
import com.yourapp.iptv.data.datastore.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileIsolationTest {
    private fun people() = ProfilePeople(UserProfiles(UserProfiles().entries + profileRow() + profileRow(PROFILE_B),PROFILE_A))
    @Test fun queuedHistoryAndVodWritesKeepTheirCapturedProfileAfterSwitch() = runTest {
        val people = people();val settings = ProfileSettings();val disk = ProfileMemory()
        val history = WatchHistoryDataStore(disk,settings,backgroundScope,people)
        val progress = VodProgressDataStore(disk,settings,backgroundScope,people)
        val raw = PlaylistCacheKey.of(settings.values.value);val a = ProfileScope.key(PROFILE_A,raw);val b = ProfileScope.key(PROFILE_B,raw)
        history.recordStarted(a,WatchHistoryEntry("old",1));progress.record(a,VodProgressEntry(VodKind.MOVIE,"42","Title","mp4",60000,200000,1))
        people.state.value = people.state.value.copy(catalog = people.state.value.catalog.copy(activeId = PROFILE_B))
        history.recordStarted(b,WatchHistoryEntry("new",2));runCurrent()
        assertEquals("old",history.observe(a).first().single().channelId);assertEquals("new",history.observe(b).first().single().channelId)
        assertEquals("42",progress.observe(a).first().single().id);assertTrue(progress.observe(b).first().isEmpty())
    }
    @Test fun deletedProfileRejectsQueuedRecordsAndSearchWrites() = runTest {
        val people = people();val settings = ProfileSettings();val disk = ProfileMemory()
        val history = WatchHistoryDataStore(disk,settings,backgroundScope,people)
        val progress = VodProgressDataStore(disk,settings,backgroundScope,people)
        val a = ProfileScope.key(PROFILE_A,PlaylistCacheKey.of(settings.values.value))
        history.recordStarted(a,WatchHistoryEntry("old",1));progress.record(a,VodProgressEntry(VodKind.MOVIE,"42","Title","mp4",60000,200000,1))
        people.state.value = people.state.value.copy(catalog = UserProfiles(UserProfiles().entries + profileRow(PROFILE_B)))
        runCurrent();assertTrue(disk.values.value.asMap().isEmpty())
        assertTrue(runCatching { SearchHistoryDataStore(disk,people).record(a,"Query") }.isFailure)
    }
    @Test fun historyRetentionAndSearchHistoryAreIndependentForEveryProfile() = runTest {
        val people = people();val settings = ProfileSettings();val disk = ProfileMemory()
        val history = WatchHistoryDataStore(disk,settings,backgroundScope,people);val search = SearchHistoryDataStore(disk,people)
        for (id in listOf(PROFILE_A,PROFILE_B)) repeat(36) { index ->
            settings.values.value = AppSettings(m3uUrl = "https://synthetic.invalid/$index")
            val key = ProfileScope.key(id,PlaylistCacheKey.of(settings.values.value))
            history.recordStarted(key,WatchHistoryEntry(id,index.toLong()));search.record(key,"$id $index");runCurrent()
        }
        assertEquals(64,disk.values.value.asMap().keys.count { it.name.startsWith("history_") })
        assertEquals(64,disk.values.value.asMap().keys.count { it.name.startsWith("search_") })
    }
    @Test fun defaultSettingsStayInTheirLegacyStoreAndProfileAppearanceHasItsOwnOwner() = runBlocking {
        ProfileFixture().use { f -> f.start();val legacy = ProfileMemory()
            val home = HomeAppearanceDataStore(legacy,f.repository);val player = PlayerAppearanceDataStore(legacy,f.repository)
            home.save(HomeAppearance(language = HomeLanguage.ENGLISH));f.repository.create("A",ProfileKind.ADULT);val a = f.last().id
            f.repository.select(a);assertEquals(a,home.observe().first().profileId)
            home.save(HomeAppearance(language = HomeLanguage.GERMAN,profileId = a));player.save(PlayerAppearance(profileId = a))
            f.repository.select("default");assertEquals(HomeLanguage.ENGLISH,home.observe().first().language)
            assertEquals("default",home.observe().first().profileId)
        }
    }
    @Test fun scopedSettingsDoNotDuplicateOrPersistSubscriptionCredentials() = runBlocking {
        val raw = ProfileSettings();val people = people();val wrapper = ProfileSettingsRepository(raw,people)
        assertEquals(PROFILE_A,wrapper.observeSettings().first().profileId)
        wrapper.saveSettings(raw.values.value.copy(profileId = PROFILE_A))
        assertEquals("default",raw.values.value.profileId)
    }
}