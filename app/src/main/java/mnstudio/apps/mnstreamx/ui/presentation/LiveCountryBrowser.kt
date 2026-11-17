package mnstudio.apps.mnstreamx.ui.presentation

import java.text.Collator
import java.util.Locale
import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.organization.FolderLabel
import mnstudio.apps.mnstreamx.domain.organization.OrganizationTree
import mnstudio.apps.mnstreamx.domain.organization.lexicon.CountryLexicon

internal data class LiveCountry(val code: String, val name: String, val groups: List<LiveProviderGroup>)

internal data class LiveProviderGroup(val name: String?, val channels: List<Channel>) {
    val key: String get() = name?.let { "group:$it" } ?: "ungrouped"
}

internal object LiveCountryBrowser {
    private val countryCodes = Locale.getISOCountries().toSet()
    private val prefix = Regex("""^(?:\|([a-z]{2})\||\[([a-z]{2})\]|([a-z]{2})\s*[:|–-])""", RegexOption.IGNORE_CASE)
    private val folders = buildMap {
        CountryLexicon.entries.forEach { entry ->
            val label = entry.value.leaf ?: when (entry.id) {
                "pl" -> FolderLabel.POLISH
                "de" -> FolderLabel.GERMAN
                else -> null
            }
            if (label != null) put(label, entry.id.uppercase(Locale.ROOT))
        }
    }

    fun project(
        channels: List<Channel>,
        tree: OrganizationTree,
        locale: Locale,
        unclassified: String,
        visible: (Channel) -> Boolean,
        checkpoint: () -> Unit
    ): List<LiveCountry> {
        val byProviderGroup = mutableMapOf<String?, String>()
        val countries = linkedMapOf<String, MutableMap<String?, MutableList<Channel>>>()
        val seen = hashSetOf<String>()
        for (channel in channels) {
            checkpoint()
            if (!visible(channel) || !seen.add(channel.id)) continue
            val country = channel.folderIds.asReversed().firstNotNullOfOrNull { id -> tree.folder(id)?.label?.let(folders::get) }
                ?: byProviderGroup.getOrPut(channel.group) { countryCode(channel.group) }
            val groups = countries.getOrPut(country) { linkedMapOf() }
            groups.getOrPut(channel.group?.takeUnless { it.isBlank() }) { mutableListOf() }.add(channel)
        }
        val collator = Collator.getInstance(locale)
        return countries.map { (code, groups) ->
            val name = if (code.isEmpty()) unclassified else Locale.Builder().setRegion(code).build().getDisplayCountry(locale)
            LiveCountry(code, name, groups.map { (group, members) -> LiveProviderGroup(group, members) })
        }.sortedWith { a, b ->
            when {
                a.code == b.code -> 0
                a.code.isEmpty() -> 1
                b.code.isEmpty() -> -1
                else -> collator.compare(a.name, b.name).takeIf { it != 0 } ?: a.code.compareTo(b.code)
            }
        }
    }

    private fun countryCode(group: String?): String {
        val value = group?.trimStart().orEmpty()
        val code = prefix.find(value)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.uppercase(Locale.ROOT)
        val iso = if (code == "UK") "GB" else code
        if (iso != null && iso in countryCodes) return iso
        if (value.length >= 4) {
            val first = value.codePointAt(0) - 0x1F1E6
            val second = value.codePointAt(2) - 0x1F1E6
            if (first in 0..25 && second in 0..25) {
                val flag = "${'A' + first}${'A' + second}"
                if (flag in countryCodes) return flag
            }
        }
        return CountryLexicon.find(value)?.id?.uppercase(Locale.ROOT).orEmpty()
    }
}