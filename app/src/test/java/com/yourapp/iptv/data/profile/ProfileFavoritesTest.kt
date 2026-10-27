package com.yourapp.iptv.data.profile

import android.app.Application
import androidx.datastore.preferences.core.*
import androidx.room.Room
import com.yourapp.iptv.data.local.AppDatabase
import com.yourapp.iptv.data.local.entity.ScopedFavoriteEntity
import com.yourapp.iptv.data.repository.FavoriteRepositoryImpl
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.profile.*
import com.yourapp.iptv.testing.SearchAccounts
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ProfileFavoritesTest {
    @Test fun sharedChannelMemoryDoesNotKeepThePreviousProfilesFavoriteFlags() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),AppDatabase::class.java).build()
        try {
            val source = AppSettings(m3uUrl = "https://synthetic.invalid/list")
            val key = PlaylistCacheKey.of(source)
            database.channelDao().insertChannels(listOf(com.yourapp.iptv.data.local.entity.ChannelEntity("42","Title",null,"https://synthetic.invalid/42",null)))
            val people = ProfilePeople(UserProfiles(UserProfiles().entries + profileRow(),PROFILE_A))
            val favorites = object : com.yourapp.iptv.domain.repository.FavoriteRepository {
                override fun observeFavoriteIds(sourceKey: String?) = flowOf(if (sourceKey == ProfileScope.key(PROFILE_A,key)) setOf("42") else emptySet())
                override suspend fun toggleFavorite(channelId: String,sourceKey: String?) = Unit
                override suspend fun setFavorite(channelId: String,selected: Boolean,sourceKey: String?) = Unit
            }
            val snapshot = object : com.yourapp.iptv.data.cache.ChannelSnapshotStore {
                override suspend fun read() = key
                override suspend fun write(fingerprint: String?) = Unit
            }
            val repository = com.yourapp.iptv.data.repository.ChannelRepositoryImpl(
                com.yourapp.iptv.data.source.m3u.M3uDataSource(),com.yourapp.iptv.data.source.xtream.XtreamDataSource(),
                database.channelDao(),database.favoriteDao(),snapshot,favorites,people)
            assertTrue(repository.getCachedChannels(source)!!.single().isFavorite)
            people.state.value = people.state.value.copy(catalog = people.state.value.catalog.copy(activeId = "default"))
            assertFalse(repository.getPlaybackChannels(source,setOf("42")).single().isFavorite)
            assertFalse(repository.getCachedChannels(source)!!.single().isFavorite)
            people.state.value = people.state.value.copy(catalog = people.state.value.catalog.copy(activeId = PROFILE_A))
            assertTrue(repository.getCachedChannels(source)!!.single().isFavorite)
        } finally { database.close() }
    }
    @Test fun roomFavoritesAreIsolatedAndRemovingAProfileCannotRemoveLegacyOrAnotherProfilesRows() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),AppDatabase::class.java).build()
        try {
            val account = AppSettings(m3uUrl = "https://synthetic.invalid/list")
            val subscriptions = object : com.yourapp.iptv.domain.repository.SubscriptionRepository {
                private val catalog = Subscriptions(listOf(Subscription("default","A",account)),"default")
                override fun observeSubscriptions() = flowOf(catalog)
                override fun observeSettings() = flowOf(account)
                override suspend fun saveSettings(settings: AppSettings) = Unit
                override suspend fun saveSubscription(id: String?,name: String,settings: AppSettings) = catalog
                override suspend fun activateSubscription(id: String) = catalog
                override suspend fun removeSubscription(id: String) = catalog
            }
            val people = ProfilePeople(UserProfiles(UserProfiles().entries + profileRow() + profileRow(PROFILE_B)))
            val repository = FavoriteRepositoryImpl(database.scopedFavoriteDao(),subscriptions,people)
            val raw = PlaylistCacheKey.of(account);val a = ProfileScope.key(PROFILE_A,raw);val b = ProfileScope.key(PROFILE_B,raw)
            repository.setFavorite("same-channel",true,raw);repository.setFavorite("same-channel",true,a)
            assertTrue(repository.observeFavoriteIds(b).first().isEmpty())
            repository.setFavorite("other",true,b)
            val prefs = ProfileMemory(mutablePreferencesOf(stringPreferencesKey("history_$a") to "A",stringPreferencesKey("history_$b") to "B"))
            people.state.value = people.state.value.copy(catalog = UserProfiles(UserProfiles().entries + profileRow(PROFILE_B)))
            com.yourapp.iptv.data.backup.AppDataGate.write { ProfileCleaner(database.profileFavoriteDao(),listOf(prefs)).purge(PROFILE_A) }
            assertTrue(repository.observeFavoriteIds(a).first().isEmpty());assertEquals(setOf("same-channel"),repository.observeFavoriteIds(raw).first())
            assertEquals(setOf("other"),repository.observeFavoriteIds(b).first());assertEquals("B",prefs.values.value[stringPreferencesKey("history_$b")])
            assertNull(prefs.values.value[stringPreferencesKey("history_$a")])
            assertTrue(runCatching { repository.setFavorite("late",true,a) }.isFailure)
        } finally { database.close() }
    }
}