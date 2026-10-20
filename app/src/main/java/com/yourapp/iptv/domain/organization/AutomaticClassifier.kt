package com.yourapp.iptv.domain.organization

import com.yourapp.iptv.domain.organization.lexicon.CountryLexicon
import com.yourapp.iptv.domain.organization.lexicon.NetworkLexicon

internal data class AutomaticFolder(val root: String, val leaf: FolderLabel?, val reason: AssignmentReason)

/** Network exceptions first, then provider group, then the cached group-wide vote. */
internal class AutomaticClassifier(private val detectGroup: (String?) -> String? = { null }) {
    private data class GroupHint(val root: String?, val bein: Boolean)
    private val groups = mutableMapOf<String?, GroupHint>()
    private val roots = setOf("polish", "german", "arabic")

    fun classify(group: String?, name: String): AutomaticFolder {
        val hint = groups.getOrPut(group) {
            val text = OrganizationNames.normalizeMatch(group.orEmpty())
            GroupHint(root(text), NetworkLexicon.find(text) == "bein")
        }
        if (!NetworkLexicon.isBeinEntertainment(name) &&
            (NetworkLexicon.find(name) == "bein" || hint.bein)) {
            return AutomaticFolder("bein", null, AssignmentReason.NETWORK)
        }
        val identified = hint.root ?: detectGroup(group)?.takeIf { it in roots }
        return if (identified != null) AutomaticFolder(identified, null, AssignmentReason.COUNTRY)
            else AutomaticFolder("unclassified", null, AssignmentReason.UNCLASSIFIED)
    }

    private fun root(text: String): String? {
        when (text.substringBefore(' ')) {
            "pl" -> return "polish"
            "de" -> return "german"
            "ar" -> return "arabic"
        }
        if (listOf("arab", "arabic", "arabische", "العربيه", "عربي", "العرب")
                .any { (" $text ").contains(" $it ") }) return "arabic"
        return CountryLexicon.find(text)?.value?.root
    }
}