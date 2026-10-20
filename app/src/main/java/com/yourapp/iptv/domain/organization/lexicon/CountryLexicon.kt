package com.yourapp.iptv.domain.organization.lexicon

import com.yourapp.iptv.domain.organization.FolderLabel
import com.yourapp.iptv.domain.organization.OrganizationNames

internal data class CountryTarget(val root: String?, val leaf: FolderLabel?)

/** 33 ISO 3166-1 countries; only pre-existing folder roots are routable. */
internal object CountryLexicon {
    private fun row(iso: String, leaf: FolderLabel?, ar: String, en: String, de: String,
        pl: String, fr: String, es: String, it: String, tr: String, vararg aliases: String
    ): LexiconEntry<CountryTarget> {
        val root = when (iso) { "de" -> "german"; "pl" -> "polish"
            else -> if (leaf != null) "arabic" else null }
        return LexiconEntry(iso, CountryTarget(root, leaf),
            mapOf("ar" to ar, "en" to en, "de" to de, "pl" to pl,
                "fr" to fr, "es" to es, "it" to it, "tr" to tr), aliases.toList())
    }

    val entries = listOf(
        row("sy", FolderLabel.SYRIA, "سوريا", "Syria", "Syrien", "Syria", "Syrie", "Siria", "Siria", "Suriye", "syrian", "سورية", "سوريه", "السورية", "السوريه"),
        row("eg", FolderLabel.EGYPT, "مصر", "Egypt", "Ägypten", "Egipt", "Égypte", "Egipto", "Egitto", "Mısır", "egyptian", "agypten", "المصرية", "المصريه"),
        row("lb", FolderLabel.LEBANON, "لبنان", "Lebanon", "Libanon", "Liban", "Liban", "Líbano", "Libano", "Lübnan", "lebanese", "اللبنانية", "اللبنانيه"),
        row("iq", FolderLabel.IRAQ, "العراق", "Iraq", "Irak", "Irak", "Irak", "Irak", "Iraq", "Irak", "iraqi", "العراقيه"),
        row("jo", FolderLabel.JORDAN, "الأردن", "Jordan", "Jordanien", "Jordania", "Jordanie", "Jordania", "Giordania", "Ürdün", "jordanian", "الاردنيه"),
        row("ps", FolderLabel.PALESTINE, "فلسطين", "Palestine", "Palästina", "Palestyna", "Palestine", "Palestina", "Palestina", "Filistin", "palestinian", "palastina", "الفلسطينية"),
        row("sa", FolderLabel.SAUDI, "السعودية", "Saudi Arabia", "Saudi Arabien", "Arabia Saudyjska", "Arabie saoudite", "Arabia Saudita", "Arabia Saudita", "Suudi Arabistan", "saudi", "ksa", "سعودي", "السعوديه"),
        row("ae", FolderLabel.UAE, "الإمارات", "United Arab Emirates", "Vereinigte Arabische Emirate", "Zjednoczone Emiraty Arabskie", "Émirats arabes unis", "Emiratos Árabes Unidos", "Emirati Arabi Uniti", "Birleşik Arap Emirlikleri", "uae", "emirates", "الامارات", "الاماراتيه"),
        row("kw", FolderLabel.KUWAIT, "الكويت", "Kuwait", "Kuwait", "Kuwejt", "Koweït", "Kuwait", "Kuwait", "Kuveyt", "الكويتيه"),
        row("qa", FolderLabel.QATAR, "قطر", "Qatar", "Katar", "Katar", "Qatar", "Catar", "Qatar", "Katar", "القطريه"),
        row("bh", FolderLabel.BAHRAIN, "البحرين", "Bahrain", "Bahrain", "Bahrajn", "Bahreïn", "Baréin", "Bahrein", "Bahreyn", "البحرينيه"),
        row("om", FolderLabel.OMAN, "عُمان", "Oman", "Oman", "Oman", "Oman", "Omán", "Oman", "Umman", "عمان", "عمانيه", "العمانيه"),
        row("ma", FolderLabel.MOROCCO, "المغرب", "Morocco", "Marokko", "Maroko", "Maroc", "Marruecos", "Marocco", "Fas", "moroccan", "المغربيه"),
        row("dz", FolderLabel.ALGERIA, "الجزائر", "Algeria", "Algerien", "Algieria", "Algérie", "Argelia", "Algeria", "Cezayir", "algerian", "الجزائريه"),
        row("tn", FolderLabel.TUNISIA, "تونس", "Tunisia", "Tunesien", "Tunezja", "Tunisie", "Túnez", "Tunisia", "Tunus", "tunisian", "التونسيه"),
        row("ly", FolderLabel.LIBYA, "ليبيا", "Libya", "Libyen", "Libia", "Libye", "Libia", "Libia", "Libya", "libyan", "الليبيه"),
        row("sd", FolderLabel.SUDAN, "السودان", "Sudan", "Sudan", "Sudan", "Soudan", "Sudán", "Sudan", "Sudan", "sudanese", "السودانيه"),
        row("ye", FolderLabel.YEMEN, "اليمن", "Yemen", "Jemen", "Jemen", "Yémen", "Yemen", "Yemen", "Yemen", "yemeni", "اليمنيه"),
        row("mr", FolderLabel.MAURITANIA, "موريتانيا", "Mauritania", "Mauretanien", "Mauretania", "Mauritanie", "Mauritania", "Mauritania", "Moritanya", "الموريتانيه"),
        row("so", FolderLabel.SOMALIA, "الصومال", "Somalia", "Somalia", "Somalia", "Somalie", "Somalia", "Somalia", "Somali", "somali", "الصوماليه"),
        row("dj", FolderLabel.DJIBOUTI, "جيبوتي", "Djibouti", "Dschibuti", "Dżibuti", "Djibouti", "Yibuti", "Gibuti", "Cibuti"),
        row("km", FolderLabel.COMOROS, "جزر القمر", "Comoros", "Komoren", "Komory", "Comores", "Comoras", "Comore", "Komorlar"),
        row("de", null, "ألمانيا", "Germany", "Deutschland", "Niemcy", "Allemagne", "Alemania", "Germania", "Almanya", "german", "deutsche", "deutsch", "المانيا", "الالمانيه"),
        row("pl", null, "بولندا", "Poland", "Polen", "Polska", "Pologne", "Polonia", "Polonia", "Polonya", "polish", "polskie", "البولنديه"),
        row("fr", null, "فرنسا", "France", "Frankreich", "Francja", "France", "Francia", "Francia", "Fransa", "french", "francais"),
        row("es", null, "إسبانيا", "Spain", "Spanien", "Hiszpania", "Espagne", "España", "Spagna", "İspanya", "spanish"),
        row("it", null, "إيطاليا", "Italy", "Italien", "Włochy", "Italie", "Italia", "Italia", "İtalya", "italian"),
        row("tr", null, "تركيا", "Turkey", "Türkei", "Turcja", "Turquie", "Turquía", "Turchia", "Türkiye", "turkish"),
        row("gb", null, "المملكة المتحدة", "United Kingdom", "Vereinigtes Königreich", "Wielka Brytania", "Royaume-Uni", "Reino Unido", "Regno Unito", "Birleşik Krallık", "uk", "britain", "england"),
        row("us", null, "الولايات المتحدة", "United States", "Vereinigte Staaten", "Stany Zjednoczone", "États-Unis", "Estados Unidos", "Stati Uniti", "Amerika Birleşik Devletleri", "usa", "america"),
        row("ca", null, "كندا", "Canada", "Kanada", "Kanada", "Canada", "Canadá", "Canada", "Kanada"),
        row("br", null, "البرازيل", "Brazil", "Brasilien", "Brazylia", "Brésil", "Brasil", "Brasile", "Brezilya"),
        row("in", null, "الهند", "India", "Indien", "Indie", "Inde", "India", "India", "Hindistan"),
    )

    init { require(entries.size == 33 && entries.map { it.id }.distinct().size == 33) }

    /** ISO codes are checked only at provider-prefix boundaries. */
    private fun codeAtStart(text: String, code: String, allowArPrefix: Boolean) =
        text == code || text.startsWith("$code ") ||
        (allowArPrefix && (text == "ar $code" || text.startsWith("ar $code ")))

    fun find(text: String, arabOnly: Boolean = false, allowCode: Boolean = true): LexiconEntry<CountryTarget>? {
        val normalized = OrganizationNames.normalizeMatch(text)
        val candidates = if (arabOnly) entries.filter { it.value.leaf != null } else entries
        val byName = LexiconMatcher.match(normalized, candidates)
        if (byName != null) return byName
        return if (allowCode) candidates.firstOrNull { codeAtStart(normalized, it.id, arabOnly) } else null
    }
}