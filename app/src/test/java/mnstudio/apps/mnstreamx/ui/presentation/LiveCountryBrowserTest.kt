package mnstudio.apps.mnstreamx.ui.presentation

import java.util.Locale
import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.organization.ChannelFolder
import mnstudio.apps.mnstreamx.domain.organization.FolderLabel
import mnstudio.apps.mnstreamx.domain.organization.OrganizationTree
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveCountryBrowserTest {
    @Test fun localizedCountriesKeepProviderGroupsAndTheirChannelOrder() {
        val catalog = listOf(channel("one", "|PL| Sport"), channel("two", "[PL] News"),
            channel("three", "|PL| Sport"), channel("four", "🇩🇪 Regional"), channel("five", "Sport International"))
        val countries = project(catalog)
        assertEquals(listOf("DE", "PL", ""), countries.map { it.code })
        assertEquals(listOf("Deutschland", "Polen", "Nicht zugeordnet"), countries.map { it.name })
        assertEquals(listOf("|PL| Sport", "[PL] News"), countries[1].groups.map { it.name })
        assertEquals(listOf("one", "three"), countries[1].groups.first().channels.map { it.id })
    }

    @Test fun folderAssignmentWinsOverTheProviderCountryAndHiddenChannelsAreExcluded() {
        val tree = OrganizationTree(true, listOf(ChannelFolder("polish", label = FolderLabel.POLISH)))
        val catalog = listOf(channel("moved", "DE: Regional").copy(folderIds = listOf("polish")),
            channel("hidden", "FR: News"))
        val result = project(catalog, tree) { it.id != "hidden" }
        assertEquals(listOf("PL"), result.map { it.code })
        assertEquals("DE: Regional", result.single().groups.single().name)
    }

    private fun channel(id: String, group: String) = Channel(id, id, streamUrl = "synthetic:$id", group = group)

    private fun project(channels: List<Channel>, tree: OrganizationTree = OrganizationTree(),
        visible: (Channel) -> Boolean = { true }) =
        LiveCountryBrowser.project(channels, tree, Locale.GERMAN, "Nicht zugeordnet", visible) {}
}