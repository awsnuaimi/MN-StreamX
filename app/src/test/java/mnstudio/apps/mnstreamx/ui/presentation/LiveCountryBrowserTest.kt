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

    @Test fun arabicAndArgentineProviderGroupsUseDifferentCodesAndNames() {
        val arabic = listOf("AR", "AR SYRIA", "|AR| Sport", "[ar] News", "AR: Movies")
        val argentina = listOf("ARG", "arg Sports", "|ARG| Sports", "[ARG] News", "ARG: Movies",
            "🇦🇷 Nacional", "Argentina: Regional", "Argentinien", "الأرجنتين")
        val catalog = (arabic + argentina).mapIndexed { index, group -> channel(index.toString(), group) }
        val countries = project(catalog).associateBy { it.code }
        assertEquals(setOf("AR", "ARG"), countries.keys)
        assertEquals("Arabisch", countries.getValue("AR").name)
        assertEquals(arabic, countries.getValue("AR").groups.map { it.name })
        assertEquals("Argentinien", countries.getValue("ARG").name)
        assertEquals(argentina, countries.getValue("ARG").groups.map { it.name })
    }

    @Test fun arabicFolderUsesTheArabicCodeWithoutAProviderPrefix() {
        val tree = OrganizationTree(true, listOf(ChannelFolder("arabic", label = FolderLabel.ARABIC)))
        val catalog = listOf(channel("one", "International").copy(folderIds = listOf("arabic")))
        assertEquals("AR", project(catalog, tree).single().code)
    }

    private fun channel(id: String, group: String) = Channel(id, id, streamUrl = "synthetic:$id", group = group)

    private fun project(channels: List<Channel>, tree: OrganizationTree = OrganizationTree(),
        visible: (Channel) -> Boolean = { true }) =
        LiveCountryBrowser.project(channels, tree, Locale.GERMAN, "Nicht zugeordnet", visible) {}
}