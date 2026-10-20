package com.yourapp.iptv.domain.organization

internal data class AutomaticFolder(val root: String, val leaf: FolderLabel?, val reason: AssignmentReason)

internal class AutomaticClassifier {
    private data class GroupHint(val text: String, val country: FolderLabel?, val root: String?, val genre: FolderLabel?, val network: Boolean)
    private val groups = mutableMapOf<String?, GroupHint>()
    fun classify(group: String?, name: String): AutomaticFolder {
        val hint = groups.getOrPut(group) {
            val text = OrganizationNames.normalize(group.orEmpty())
            GroupHint(text, arabCountry(text), root(text), genre(text), network(text))
        }
        val excludedBein = entertainmentPattern.containsMatchIn(name)
        if (!excludedBein && (network(name) || hint.network)) return AutomaticFolder("bein", null, AssignmentReason.NETWORK)
        if (hint.country != null) return AutomaticFolder("arabic", hint.country, AssignmentReason.COUNTRY)
        if (hint.root == "german" || hint.root == "polish") return AutomaticFolder(hint.root,
            hint.genre ?: genre(name) ?: FolderLabel.GENERAL, AssignmentReason.COUNTRY)
        val country = arabCountry(name, allowCode = hint.root == "arabic")
        if (country != null) return AutomaticFolder("arabic", country, AssignmentReason.COUNTRY)
        val root = hint.root ?: root(name)
        if (root == "arabic") return AutomaticFolder(root, FolderLabel.ARAB_MISC, AssignmentReason.COUNTRY)
        if (root != null) return AutomaticFolder(root, hint.genre ?: genre(name) ?: FolderLabel.GENERAL, AssignmentReason.COUNTRY)
        return AutomaticFolder("unclassified", null, AssignmentReason.UNCLASSIFIED)
    }
    private fun network(text: String) = networkPattern.containsMatchIn(text)
    private fun root(text: String): String? = when {
        prefix(text, "de") || has(text, "germany", "german", "deutschland", "deutsche", "المانيا", "الالمانيه") -> "german"
        prefix(text, "pl") || has(text, "poland", "polska", "polish", "polskie", "بولندا", "البولنديه") -> "polish"
        prefix(text, "ar") || has(text, "arab", "arabic", "arabische", "العربيه", "عربي", "العرب") -> "arabic"
        else -> null
    }
    private fun arabCountry(text: String, allowCode: Boolean = true): FolderLabel? = countries.firstOrNull { (code, _, names) ->
        (allowCode && (prefix(text, code) || text.startsWith("ar $code ") || text == "ar $code")) || names.any { has(text, it) }
    }?.second
    private fun genre(text: String): FolderLabel? = genres.firstOrNull { (_, names) -> names.any { has(text, it) } }?.first
    private fun has(text: String, vararg values: String): Boolean {
        val padded = " $text "
        return values.any { padded.contains(" $it ") }
    }
    private fun prefix(text: String, value: String) = text == value || text.startsWith("$value ")
    companion object {
        private val networkPattern = Regex("(?:^| )(?:bein ?sports?(?: ?[0-9]+)?|bein [0-9]+|بي ان سبورت|بين سبورت)(?: |$)")
        private val entertainmentPattern = Regex("(?:^| )bein ?(?:movies?|cinema|drama|entertainment)(?: |[0-9]|$)")
        private val countries = listOf(
            Triple("sy", FolderLabel.SYRIA, listOf("syria", "syrian", "syrien", "سوريا", "سوريه", "السوريه")),
            Triple("eg", FolderLabel.EGYPT, listOf("egypt", "egyptian", "agypten", "مصر", "المصريه")),
            Triple("lb", FolderLabel.LEBANON, listOf("lebanon", "lebanese", "libanon", "لبنان", "اللبنانيه")),
            Triple("iq", FolderLabel.IRAQ, listOf("iraq", "iraqi", "irak", "العراق", "العراقيه")),
            Triple("jo", FolderLabel.JORDAN, listOf("jordan", "jordanian", "jordanien", "الاردن", "الاردنيه")),
            Triple("ps", FolderLabel.PALESTINE, listOf("palestine", "palestinian", "palastina", "فلسطين", "الفلسطينيه")),
            Triple("sa", FolderLabel.SAUDI, listOf("saudi", "ksa", "السعوديه", "سعودي")),
            Triple("ae", FolderLabel.UAE, listOf("uae", "emirates", "الامارات", "الاماراتيه")),
            Triple("kw", FolderLabel.KUWAIT, listOf("kuwait", "الكويت", "الكويتيه")),
            Triple("qa", FolderLabel.QATAR, listOf("qatar", "قطر", "القطريه")),
            Triple("bh", FolderLabel.BAHRAIN, listOf("bahrain", "البحرين", "البحرينيه")),
            Triple("om", FolderLabel.OMAN, listOf("oman", "عمانيه", "العمانيه")),
            Triple("ma", FolderLabel.MOROCCO, listOf("morocco", "moroccan", "marokko", "المغرب", "المغربيه")),
            Triple("dz", FolderLabel.ALGERIA, listOf("algeria", "algerian", "algerien", "الجزائر", "الجزائريه")),
            Triple("tn", FolderLabel.TUNISIA, listOf("tunisia", "tunisian", "tunesien", "تونس", "التونسيه")),
            Triple("ly", FolderLabel.LIBYA, listOf("libya", "libyan", "libyen", "ليبيا", "الليبيه")),
            Triple("sd", FolderLabel.SUDAN, listOf("sudan", "sudanese", "السودان", "السودانيه")),
            Triple("ye", FolderLabel.YEMEN, listOf("yemen", "yemeni", "jemen", "اليمن", "اليمنيه")),
            Triple("mr", FolderLabel.MAURITANIA, listOf("mauritania", "mauretanien", "موريتانيا", "الموريتانيه")),
            Triple("so", FolderLabel.SOMALIA, listOf("somalia", "somali", "الصومال", "الصوماليه")),
            Triple("dj", FolderLabel.DJIBOUTI, listOf("djibouti", "جيبوتي")),
            Triple("km", FolderLabel.COMOROS, listOf("comoros", "جزر القمر"))
        )
        private val genres = listOf(
            FolderLabel.SPORTS to listOf("sport", "sports", "sportowe", "eurosport", "sport1", "رياضه", "رياضيه"),
            FolderLabel.NEWS to listOf("news", "nachrichten", "informacyjne", "wiadomosci", "اخبار", "اخباريه"),
            FolderLabel.MOVIES to listOf("movies", "movie", "cinema", "film", "filme", "filmowe", "kino", "افلام"),
            FolderLabel.KIDS to listOf("kids", "children", "kinder", "bajki", "dzieci", "dzieciece", "اطفال"),
            FolderLabel.DOCUMENTARY to listOf("documentary", "documentaries", "doku", "dokumentation", "dokumentalne", "وثائقيه", "وثائقيات"),
            FolderLabel.MUSIC to listOf("music", "musik", "muzyczne", "موسيقي", "موسيقيه"),
            FolderLabel.GENERAL to listOf("general", "ogolnotematyczne", "عام", "عامه")
        )
    }
}