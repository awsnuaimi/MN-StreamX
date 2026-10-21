package com.yourapp.iptv.data.repository

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.domain.repository.SettingsRepository
import com.yourapp.iptv.viewmodel.OrganizationMemory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileHomeStartupRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val source = AppSettings(m3uUrl = "https://unit.test/list")
    private val settings = Settings(source)
    private val organization = OrganizationMemory(OrganizationConfig(
        sourceKey = OrganizationNames.sourceKey(source), enabled = true))
    private val rows = listOf(Channel("one", "TVP 1", streamUrl = "https://unit.test/one", group = "PL News"))

    @Test fun newRepositoryRestoresTheCatalogWithoutAnyChannelRepositoryDependency() = runBlocking {
        val file = temporary.root.resolve("catalog.bin")
        val config = organization.value.value
        FileHomeStartupRepository(file, settings, organization).save(source, config,
            ChannelOrganizer.project(rows, config), 5000)
        val reopened = FileHomeStartupRepository(file, settings, organization).restore(source)
        assertEquals("one", reopened!!.catalog.channels.single().id)
        assertEquals(5000L, reopened.refreshedAtMillis)
        assertEquals(0, organization.writes)
    }

    @Test fun anotherSourceAndChangedRulesCannotLoadTheOldFile() = runBlocking {
        val file = temporary.root.resolve("catalog.bin")
        val repo = FileHomeStartupRepository(file, settings, organization)
        val config = organization.value.value
        repo.save(source, config, ChannelOrganizer.project(rows, config), 1)
        settings.value.value = source.copy(m3uUrl = "https://unit.test/other")
        assertNull(repo.restore(source))
        assertNull(repo.restore(settings.value.value))
        settings.value.value = source
        organization.value.value = config.copy(channelOverrides = mapOf("one" to "german"))
        assertNull(repo.restore(source))
        organization.value.value = config
        assertNotNull(repo.restore(source))
    }

    @Test fun staleWriterCannotReplaceAFileForNewerRules() = runBlocking {
        val file = temporary.root.resolve("catalog.bin")
        val repo = FileHomeStartupRepository(file, settings, organization)
        val old = organization.value.value
        val current = old.copy(channelOverrides = mapOf("one" to "german"), revision = 1)
        organization.value.value = current
        repo.save(source, current, ChannelOrganizer.project(rows, current), 2)
        repo.save(source, old, ChannelOrganizer.project(rows, old), 1)
        val restored = repo.restore(source)!!
        assertEquals(2L, restored.refreshedAtMillis)
        assertEquals("german", restored.catalog.assignments.getValue("one").folderId)
    }

    @Test fun corruptFileIsAMissAndCanBeRebuilt() = runBlocking {
        val file = temporary.root.resolve("catalog.bin").apply { writeText("broken") }
        val repo = FileHomeStartupRepository(file, settings, organization)
        assertNull(repo.restore(source))
        val config = organization.value.value
        repo.save(source, config, ChannelOrganizer.project(rows, config), 1)
        assertNotNull(repo.restore(source))
    }

    private class Settings(initial: AppSettings) : SettingsRepository {
        val value = MutableStateFlow(initial)
        override fun observeSettings() = value
        override suspend fun saveSettings(settings: AppSettings) { value.value = settings }
    }
}