package com.yourapp.iptv.domain.organization

import com.yourapp.iptv.domain.organization.lexicon.CountryLexicon
import com.yourapp.iptv.domain.organization.lexicon.GenreLexicon
import com.yourapp.iptv.domain.organization.lexicon.NetworkLexicon

internal data class AutomaticFolder(val root: String, val leaf: FolderLabel?, val reason: AssignmentReason)

/** No UI locale dependency; all evidence is matched against language-independent tokens. */
internal class AutomaticClassifier {
    private data class GroupHint(
        val country: FolderLabel?, val root: String?, val genre: FolderLabel?, val bein: Boolean
    )
    private val groups = mutableMapOf<String?, GroupHint>()

    fun classify(group: String?, name: String): AutomaticFolder {
        val hint = groups.getOrPut(group) {
            val text = OrganizationNames.normalizeMatch(group.orEmpty())
            val country = CountryLexicon.find(text, arabOnly = true)?.value?.leaf
            GroupHint(country, root(text), GenreLexicon.find(text), NetworkLexicon.find(text) == "bein")
        }
        val excludedBein = NetworkLexicon.isBeinEntertainment(name)
        if (!excludedBein && (NetworkLexicon.find(name) == "bein" || hint.bein)) {
            return AutomaticFolder("bein", null, AssignmentReason.NETWORK)
        }
        if (hint.country != null) return AutomaticFolder("arabic", hint.country, AssignmentReason.COUNTRY)
        if (hint.root == "german" || hint.root == "polish") {
            return AutomaticFolder(hint.root, hint.genre ?: GenreLexicon.find(name) ?: FolderLabel.GENERAL,
                AssignmentReason.COUNTRY)
        }
        val country = CountryLexicon.find(name, arabOnly = true, allowCode = hint.root == "arabic")?.value?.leaf
        if (country != null) return AutomaticFolder("arabic", country, AssignmentReason.COUNTRY)
        val identifiedRoot = hint.root ?: root(name)
        if (identifiedRoot == "arabic") {
            // Genre children for Arabic are created on demand; old AR_MISC remains the fallback.
            return AutomaticFolder("arabic", hint.genre ?: GenreLexicon.find(name) ?: FolderLabel.ARAB_MISC,
                AssignmentReason.COUNTRY)
        }
        if (identifiedRoot != null) return AutomaticFolder(identifiedRoot,
            hint.genre ?: GenreLexicon.find(name) ?: FolderLabel.GENERAL, AssignmentReason.COUNTRY)
        return AutomaticFolder("unclassified", null, AssignmentReason.UNCLASSIFIED)
    }

    private fun root(text: String): String? {
        val normalized = OrganizationNames.normalizeMatch(text)
        if (normalized == "ar" || normalized.startsWith("ar ") ||
            listOf("arab", "arabic", "arabische", "العربيه", "عربي", "العرب")
                .any { (" $normalized ").contains(" $it ") }) return "arabic"
        return CountryLexicon.find(normalized)?.value?.root
    }
}