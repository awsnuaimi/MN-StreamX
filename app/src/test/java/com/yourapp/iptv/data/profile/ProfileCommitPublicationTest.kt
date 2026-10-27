package com.yourapp.iptv.data.profile

import com.yourapp.iptv.data.backup.AppDataGate
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.ParentalSection
import com.yourapp.iptv.domain.parental.ParentalRules
import com.yourapp.iptv.domain.parental.ParentalResult
import com.yourapp.iptv.domain.profile.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ProfileCommitPublicationTest {
    @Test fun aDurableCreatePublishesWithoutWaitingForTheObserverScheduler() = runBlocking {
        ProfilePublicationFixture().use { f ->
            f.start();val pause = f.pauseObserver()
            val writing = async(Dispatchers.Default) { f.profiles.create("Family",ProfileKind.ADULT) }
            try {
                assertEquals(ProfileResult.OK,withTimeout(3_000) { writing.await() })
                assertEquals("Family",f.profiles.state.value.catalog.entries.last().name)
                AppDataGate.write { assertEquals(f.catalog(),f.profiles.state.value.catalog) }
            } finally { pause.release();writing.join() }
        }
    }
    @Test fun selectingAChildPublishesTheIdentityAndRevokesTheAdultGrantBeforeReturning() = runBlocking {
        ProfilePublicationFixture().use { f ->
            f.start(true);f.profiles.create("Child",ProfileKind.CHILD)
            val child = f.profiles.state.value.catalog.entries.last().id
            val pause = f.pauseObserver();val writing = async(Dispatchers.Default) { f.profiles.select(child) }
            try {
                assertEquals(ProfileResult.OK,withTimeout(3_000) { writing.await() })
                assertEquals(child,f.access.state.value.profileId)
                assertFalse(f.profiles.state.value.switching);assertFalse(f.access.state.value.unlocked)
                ParentalSection.entries.forEach { assertFalse(f.access.state.value.allows(it)) }
                assertEquals(ProfileResult.PIN_REQUIRED,f.profiles.select("default"))
            } finally { pause.release();writing.join() }
        }
    }
    @Test fun personalPreferencesArePublishedWithTheirOriginalOwnerWhileObservationIsPaused() = runBlocking {
        ProfilePublicationFixture().use { f ->
            f.start();f.profiles.create("A",ProfileKind.ADULT)
            val id = f.profiles.state.value.catalog.entries.last().id
            val pause = f.pauseObserver();val writing = async(Dispatchers.Default) {
                f.profiles.saveHome(id,HomeAppearance(theme = HomeTheme.RED))
                f.profiles.savePlayer(id,PlayerAppearance())
                f.profiles.saveStartup(id,true)
            }
            try {
                withTimeout(3_000) { writing.await() }
                val current = f.profiles.state.value.catalog
                assertEquals("default",current.activeId)
                val owner = current.entries.first { it.id == id }
                assertEquals(HomeTheme.RED,owner.home?.theme);assertTrue(owner.resumeAtStartup)
                assertEquals(id,owner.home?.profileId);assertEquals(id,owner.player?.profileId)
                assertEquals(f.catalog(),current)
            } finally { pause.release();writing.join() }
        }
    }
    @Test fun parallelRenamesReadTheLatestCommittedCatalogWithoutAnObserverRoundTrip() = runBlocking {
        ProfilePublicationFixture().use { f ->
            f.start();val pause = f.pauseObserver()
            val writes = (1..24).map { n -> async(Dispatchers.Default) { f.profiles.rename("default","Home $n") } }
            try {
                assertTrue(withTimeout(3_000) { writes.awaitAll() }.all { it == ProfileResult.OK })
                assertEquals(24L,f.profiles.state.value.catalog.revision)
                assertEquals(f.catalog(),f.profiles.state.value.catalog)
            } finally { pause.release();writes.joinAll() }
        }
    }
    @Test fun aFailedWritePublishesFailureAndKeepsThePreviouslyCommittedCatalog() = runBlocking {
        val disk = ProfileMemory()
        ProfilePublicationFixture(disk).use { f ->
            f.start();f.profiles.rename("default","Before");val before = f.profiles.state.value.catalog
            disk.fail = true
            assertEquals(ProfileResult.STORAGE,f.profiles.rename("default","After"))
            assertTrue(f.profiles.state.value.failed);assertEquals(before,f.profiles.state.value.catalog)
            assertEquals(before,f.catalog());assertFalse(f.access.state.value.allows(ParentalSection.LIVE))
        }
    }
    @Test fun savingRulesClosesTheSynchronousGuardWithoutWaitingForAnObserver() = runBlocking {
        ProfilePublicationFixture().use { f ->
            f.start(true);f.profiles.create("Adult",ProfileKind.ADULT)
            f.profiles.select(f.profiles.state.value.catalog.entries.last().id)
            assertTrue(f.access.state.value.unlocked)
            val pause = f.pauseObserver();val writing = async(Dispatchers.Default) {
                f.access.saveRules(ParentalRules(setOf(ParentalSection.LIVE)))
            }
            try {
                assertEquals(ParentalResult.OK,withTimeout(3_000) { writing.await() })
                assertEquals(setOf(ParentalSection.LIVE),f.access.state.value.rules.sections)
                assertFalse(f.access.state.value.allows(ParentalSection.LIVE))
            } finally { pause.release();writing.join() }
        }
    }
}