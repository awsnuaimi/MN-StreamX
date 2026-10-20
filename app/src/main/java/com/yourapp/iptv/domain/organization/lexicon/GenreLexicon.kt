package com.yourapp.iptv.domain.organization.lexicon

import com.yourapp.iptv.domain.organization.FolderLabel
import com.yourapp.iptv.domain.organization.OrganizationNames

/** Eight persisted genre labels; transport words are deliberately excluded. */
internal object GenreLexicon {
    private fun row(label: FolderLabel, ar: String, en: String, de: String,
        pl: String, fr: String, es: String, it: String, tr: String,
        vararg aliases: String): LexiconEntry<FolderLabel> = LexiconEntry(
        label.name, label, mapOf("ar" to ar, "en" to en, "de" to de,
            "pl" to pl, "fr" to fr, "es" to es, "it" to it, "tr" to tr), aliases.toList())

    val entries = listOf(
        row(FolderLabel.GENERAL, "عام", "General", "Allgemein", "Ogólnotematyczne",
            "Général", "General", "Generale", "Genel", "ogólne", "قنوات عامة", "كل القنوات", "عامه"),
        row(FolderLabel.SPORTS, "رياضة", "Sports", "Sport", "Sportowe",
            "Sports", "Deportes", "Sport", "Spor", "رياضيه", "رياضه", "eurosport", "sport1"),
        row(FolderLabel.NEWS, "أخبار", "News", "Nachrichten", "Informacyjne",
            "Actualités", "Noticias", "Notizie", "Haber", "اخباريه", "wiadomości", "wiadomosci"),
        row(FolderLabel.MOVIES, "أفلام", "Movies", "Filme", "Filmowe",
            "Films", "Películas", "Film", "Filmler", "cinema", "movie", "kino", "افلام"),
        row(FolderLabel.KIDS, "أطفال", "Kids", "Kinder", "Dziecięce",
            "Enfants", "Infantil", "Bambini", "Çocuk", "children", "bajki", "dzieci", "اطفال"),
        row(FolderLabel.DOCUMENTARY, "وثائقيات", "Documentary", "Dokumentation", "Dokumentalne",
            "Documentaires", "Documentales", "Documentari", "Belgesel", "documentaries", "doku", "وثائقيه"),
        row(FolderLabel.MUSIC, "موسيقى", "Music", "Musik", "Muzyczne",
            "Musique", "Música", "Musica", "Müzik", "موسيقي", "موسيقيه"),
        row(FolderLabel.OTHER, "أخرى", "Other", "Sonstige", "Inne",
            "Autres", "Otros", "Altri", "Diğer")
    )

    private val ignored = setOf("kanaly", "kanal", "channels", "sender", "tv", "iptv")

    fun find(text: String): FolderLabel? {
        val normalized = OrganizationNames.normalizeMatch(text)
        if (normalized.isBlank() || normalized in ignored) return null
        return LexiconMatcher.match(normalized, entries)?.value
    }
}