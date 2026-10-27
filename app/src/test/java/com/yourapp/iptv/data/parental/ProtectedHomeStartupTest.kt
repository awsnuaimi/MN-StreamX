package com.yourapp.iptv.data.parental

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.domain.parental.*
import com.yourapp.iptv.domain.repository.HomeStartupRepository
import com.yourapp.iptv.testing.searchAccount
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProtectedHomeStartupTest {
    private val source = searchAccount("A")
    private val channels = ParentalChannels()
    private val access = Access(ParentalState(ready = true,configured = true,
        rules = ParentalRules(groups = mapOf(PlaylistCacheKey.of(source) to setOf("Protected")))))
    private val tree = OrganizationTree(true,listOf(ChannelFolder("family",name = "Family"),ChannelFolder("protected",name = "Protected")))
        .withCounts(channels.rows)
    private val catalog = OrganizedChannels(channels.rows,tree,emptyMap(),listOf(
        SourceGroup("family","Family",1,setOf("family")),SourceGroup("protected","Protected",1,setOf("protected"))))
    private var writes = 0
    private var lastWritten: OrganizedChannels? = null
    private val raw = object : HomeStartupRepository {
        override suspend fun restore(source: AppSettings) = HomeStartupSnapshot(OrganizationConfig(),catalog,1)
        override suspend fun save(source: AppSettings, config: OrganizationConfig, catalog: OrganizedChannels, refreshedAt: Long) {
            writes++;lastWritten = catalog
        }
    }
    private val protected = ProtectedHomeStartup(raw,access)
    @Test fun startupSnapshotCannotRevealHiddenNamesPostersOrFolderCounts() = runBlocking {
        val result = requireNotNull(protected.restore(source)).catalog
        assertEquals(listOf(channels.safe),result.channels);assertNull(result.tree.folder("protected"))
        assertEquals(listOf("Family"),result.groups.map { it.name });assertEquals(1,result.tree.counts["family"])
    }
    @Test fun theUnfilteredSnapshotIsPreservedForFutureUnlockAndFilteredResultsAreNeverSaved() = runBlocking {
        val filtered = requireNotNull(protected.restore(source))
        protected.save(source,filtered.config,filtered.catalog,1);assertEquals(0,writes)
        access.state.value = access.state.value.copy(unlocked = true)
        assertEquals(channels.rows,protected.restore(source)?.catalog?.channels)
        assertEquals(2,raw.restore(source).catalog.channels.size)
    }
    @Test fun noPinPreservesTheExistingSavePathAndUnreadableProtectionReturnsNoSnapshot() = runBlocking {
        access.state.value = ParentalState(ready = true)
        protected.save(source,OrganizationConfig(),catalog,1);assertEquals(1,writes)
        access.state.value = ParentalState(ready = true,storageError = true)
        assertNull(protected.restore(source))
    }
    @Test fun productionSaveRebuildsTheCompletePrivateSnapshotFromProvenanceCheckedRawRows() = runBlocking {
        val repository = ProtectedHomeStartup(raw,access,channels)
        val filtered = requireNotNull(repository.restore(source))
        repository.save(source,filtered.config,filtered.catalog,20)
        assertEquals(listOf("1","2"),lastWritten?.channels?.map { it.id });assertEquals(1,writes)
        assertEquals(listOf("1"),repository.restore(source)?.catalog?.channels?.map { it.id })
    }
}