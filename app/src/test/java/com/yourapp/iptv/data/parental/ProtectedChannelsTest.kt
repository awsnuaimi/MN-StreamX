package com.yourapp.iptv.data.parental

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.*
import com.yourapp.iptv.data.repository.UnifiedSearchRepositoryImpl
import com.yourapp.iptv.testing.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ProtectedChannelsTest {
    private val settings = SearchAccounts()
    private val raw = ParentalChannels()
    private val access = Access(ParentalState(ready = true,configured = true,
        rules = ParentalRules(groups = mapOf(settings.key to setOf("Protected")))))
    private val channels = ProtectedChannels(raw,settings,access)
    @Test fun allReadPathsExcludeLockedGroupsIncludingFavoritesAndSinglePlaybackLookups() = runBlocking {
        val source = settings.values.value
        assertEquals(listOf(raw.safe),channels.getChannels())
        assertEquals(listOf(raw.safe),channels.getCachedChannels(source))
        assertEquals(listOf(raw.safe),channels.getPlaybackChannels(source,setOf("1","2")))
        assertEquals(listOf(raw.safe),channels.search("channel"));assertEquals(listOf(raw.safe),channels.getFavorites())
        assertNull(channels.getPlaybackChannel(source,"2"));assertNull(channels.getPlaybackChannel("2"));assertNull(channels.getChannel("2"))
        try { channels.addFavorite(raw.hidden);fail("Locked favorite accepted") } catch (_: ParentalDenied) { }
    }
    @Test fun importsKeepTheFullRawCatalogAndUnlockRestoresVisibilityWithoutReloading() = runBlocking {
        assertEquals(listOf(raw.safe),channels.loadFromXtream("https://synthetic.invalid","A","synthetic"))
        assertEquals(2,raw.rows.size);access.state.value = access.state.value.copy(unlocked = true)
        assertEquals(raw.rows,channels.getCachedChannels(settings.values.value));assertEquals(1,raw.imports)
    }
    @Test fun wholeLiveSectionLockDoesNotStartAnImportAndInitialReadIsClosed() = runBlocking {
        access.state.value = ParentalState()
        assertTrue(channels.getChannels().isEmpty())
        access.state.value = ParentalState(ready = true,configured = true,rules = ParentalRules(setOf(ParentalSection.LIVE)))
        assertTrue(channels.loadFromM3u("https://synthetic.invalid/list").isEmpty());assertEquals(0,raw.imports)
    }
    @Test fun groupRulesAreScopedAndLegacyReadsDoNotExposeAnotherSubscriptionsCache() = runBlocking {
        raw.provenance = settings.key;settings.values.value = searchAccount("B")
        assertTrue(channels.getChannels().isEmpty());assertTrue(channels.search("channel").isEmpty())
        raw.provenance = settings.key
        assertEquals(raw.rows,channels.getChannels())
    }
    @Test fun aLockDuringTheUnderlyingReadUsesTheCurrentAccessState() = runBlocking {
        access.state.value = access.state.value.copy(unlocked = true)
        val started = CompletableDeferred<Unit>();val release = CompletableDeferred<Unit>()
        raw.beforeRead = { started.complete(Unit);release.await() }
        val rows = async { channels.getCachedChannels(settings.values.value) }
        started.await();access.lock();release.complete(Unit)
        assertEquals(listOf(raw.safe),rows.await())
    }
    @Test fun theRealUnifiedSearchIndexCannotReturnHiddenChannelOrMovieTitles() = runBlocking {
        access.state.value = access.state.value.copy(rules = access.state.value.rules.copy(sections = setOf(ParentalSection.MOVIES)))
        val rawVod = ParentalVod()
        val repository = UnifiedSearchRepositoryImpl(channels,ProtectedVod(rawVod,access),Dispatchers.Unconfined)
        val index = requireNotNull(repository.channels(settings.values.value))
        assertTrue(index.find("Protected").items.isEmpty());assertEquals(1,index.find("Public").items.size)
        assertEquals(0,repository.catalog(settings.values.value,VodKind.MOVIE).index.size);assertEquals(0,rawVod.requests)
    }
}