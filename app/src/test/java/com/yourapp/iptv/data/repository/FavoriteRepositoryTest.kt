package com.yourapp.iptv.data.repository

import android.app.Application
import androidx.room.Room
import com.yourapp.iptv.data.local.AppDatabase
import com.yourapp.iptv.data.local.entity.FavoriteEntity
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.testing.FakeSubscriptions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class FavoriteRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var accounts: FakeSubscriptions
    private lateinit var repository: FavoriteRepositoryImpl
    private lateinit var first: String
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        accounts = FakeSubscriptions()
        repository = FavoriteRepositoryImpl(database.scopedFavoriteDao(), accounts)
        first = PlaylistCacheKey.of(accounts.catalog.value.active.settings)
    }
    @After fun cleanup() { database.close() }

    @Test fun repeatedToggleReadsStoredStateAndNewRepositorySeesExistingFavorites() = runBlocking {
        repository.toggleFavorite("a", first)
        val reopened = FavoriteRepositoryImpl(database.scopedFavoriteDao(), accounts)
        assertEquals(setOf("a"), reopened.observeFavoriteIds(first).first())
        repository.toggleFavorite("a", first)
        assertTrue(repository.observeFavoriteIds(first).first().isEmpty())
    }

    @Test fun repeatedSetIsIdempotentAndDoesNotCreateDuplicateRows() = runBlocking {
        repeat(3) { repository.setFavorite("a", true, first) }
        assertEquals(setOf("a"), repository.observeFavoriteIds(first).first())
        repeat(2) { repository.setFavorite("a", false, first) }
        assertTrue(repository.observeFavoriteIds(first).first().isEmpty())
    }

    @Test fun blankIdsAndMissingSourcesAreRejectedBeforeWriting() = runBlocking {
        assertTrue(runCatching { repository.toggleFavorite(" ", first) }.isFailure)
        assertTrue(runCatching { repository.toggleFavorite("a", null) }.isFailure)
        assertTrue(runCatching { repository.toggleFavorite("a", "f".repeat(64)) }.isFailure)
        assertTrue(repository.observeFavoriteIds(first).first().isEmpty())
    }

    @Test fun identicalChannelIdsRemainSeparateAcrossSubscriptionsAndSwitchingRestoresThem() = runBlocking {
        repository.toggleFavorite("shared", first)
        accounts.saveSubscription(null, "Second", FakeSubscriptions.account("B"))
        val second = PlaylistCacheKey.of(accounts.catalog.value.active.settings)
        assertTrue(repository.observeFavoriteIds(second).first().isEmpty())
        repository.toggleFavorite("other", second)
        assertEquals(setOf("shared"), repository.observeFavoriteIds(first).first())
        assertEquals(setOf("other"), repository.observeFavoriteIds(second).first())
        accounts.activateSubscription(Subscriptions.DEFAULT_ID)
        assertEquals(setOf("shared"), repository.observeFavoriteIds(first).first())
    }

    @Test fun legacyFavoritesBelongOnlyToTheOriginalSubscriptionEvenIfAnotherIsActivatedFirst() = runBlocking {
        database.favoriteDao().addFavorite(FavoriteEntity("legacy"))
        accounts.saveSubscription(null, "Second", FakeSubscriptions.account("B"))
        val second = PlaylistCacheKey.of(accounts.catalog.value.active.settings)
        assertTrue(repository.observeFavoriteIds(second).first().isEmpty())
        assertEquals(setOf("legacy"), repository.observeFavoriteIds(first).first())
        assertTrue(database.favoriteDao().getAllFavorites().isEmpty())
        repository.toggleFavorite("legacy", first)
        assertTrue(repository.observeFavoriteIds(first).first().isEmpty())
    }
}