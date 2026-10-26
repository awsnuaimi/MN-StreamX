package com.yourapp.iptv.data.subscription

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.yourapp.iptv.data.datastore.WatchHistoryDataStore
import com.yourapp.iptv.data.datastore.VodProgressDataStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.testing.FakeSubscriptions
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionHistoryTest {
    private class Memory : DataStore<Preferences> {
        private val values = MutableStateFlow<Preferences>(emptyPreferences())
        override val data = values
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(values.value).also { values.value = it }
    }
    @Test fun switchingRealHistoryRepositoriesRestoresEachAccountsLiveHistoryAndVodPosition() = runTest {
        val subscriptions = FakeSubscriptions()
        val live = WatchHistoryDataStore(Memory(), subscriptions, backgroundScope)
        val vod = VodProgressDataStore(Memory(), subscriptions, backgroundScope)
        val first = PlaylistCacheKey.of(subscriptions.catalog.value.active.settings)
        live.recordStarted(first, WatchHistoryEntry("same-channel", 100))
        vod.record(first, VodProgressEntry(VodKind.MOVIE, "42", "First", "mp4", 20_000, 200_000, 100))
        runCurrent()
        subscriptions.saveSubscription(null, "Second", FakeSubscriptions.account("B"))
        val second = PlaylistCacheKey.of(subscriptions.catalog.value.active.settings)
        assertTrue(live.observe(second).first().isEmpty()); assertTrue(vod.observe(second).first().isEmpty())
        live.recordStarted(second, WatchHistoryEntry("second-channel", 200))
        vod.record(second, VodProgressEntry(VodKind.MOVIE, "42", "Second", "mp4", 80_000, 200_000, 200))
        runCurrent()
        subscriptions.activateSubscription(Subscriptions.DEFAULT_ID)
        assertEquals("same-channel", live.observe(first).first().single().channelId)
        assertEquals(20_000L, vod.observe(first).first().single().positionMs)
        assertEquals("second-channel", live.observe(second).first().single().channelId)
        assertEquals(80_000L, vod.observe(second).first().single().positionMs)
    }
}