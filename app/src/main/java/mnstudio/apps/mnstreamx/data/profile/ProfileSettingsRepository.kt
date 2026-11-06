package mnstudio.apps.mnstreamx.data.profile

import mnstudio.apps.mnstreamx.domain.model.AppSettings
import mnstudio.apps.mnstreamx.domain.repository.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull

internal class ProfileSettingsRepository(private val source: SettingsRepository,
    private val profiles: ProfileRepository) : SettingsRepository {
    override fun observeSettings() = combine(source.observeSettings(),profiles.state) { settings, people ->
        if (!people.ready || people.failed || people.switching) null
        else settings.copy(profileId = people.catalog.activeId)
    }.filterNotNull()
    override suspend fun saveSettings(settings: AppSettings) = source.saveSettings(settings.copy(profileId = "default"))
}