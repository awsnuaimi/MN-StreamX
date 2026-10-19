package com.yourapp.iptv.data.repository

import com.yourapp.iptv.data.local.dao.FavoriteDao
import com.yourapp.iptv.data.local.entity.FavoriteEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteRepositoryTest {
    @Test fun repeatedToggleReadsStoredStateAndNewRepositorySeesExistingFavorites() = runTest {
        val dao = Favorites()
        val first = FavoriteRepositoryImpl(dao)
        first.toggleFavorite("a")
        assertEquals(setOf("a"), FavoriteRepositoryImpl(dao).observeFavoriteIds().first())
        first.toggleFavorite("a")
        assertTrue(first.observeFavoriteIds().first().isEmpty())
    }

    @Test fun observerIgnoresDuplicateIdsAndOrderOnlyChanges() = runTest {
        val dao = Favorites()
        val emissions = mutableListOf<Set<String>>()
        val repository = FavoriteRepositoryImpl(dao)
        backgroundScope.launch { repository.observeFavoriteIds().collect { emissions += it } }
        runCurrent()
        dao.ids.value = listOf("a", "b", "a")
        runCurrent()
        dao.ids.value = listOf("b", "a")
        runCurrent()
        assertEquals(listOf(emptySet<String>(), setOf("a", "b")), emissions)
    }

    @Test fun blankIdsAreRejectedBeforeWriting() = runTest {
        val dao = Favorites()
        try { FavoriteRepositoryImpl(dao).toggleFavorite(" "); fail("Expected rejection") }
        catch (_: IllegalArgumentException) { assertTrue(dao.ids.value.isEmpty()) }
    }

    private class Favorites : FavoriteDao {
        val ids = MutableStateFlow<List<String>>(emptyList())
        override fun observeFavoriteIds() = ids
        override suspend fun isFavorite(channelId: String) = channelId in ids.value
        override suspend fun getAllFavorites() = ids.value.map(::FavoriteEntity)
        override suspend fun addFavorite(favorite: FavoriteEntity) {
            ids.value = (ids.value + favorite.channelId).distinct()
        }
        override suspend fun removeFavorite(channelId: String) { ids.value = ids.value - channelId }
    }
}