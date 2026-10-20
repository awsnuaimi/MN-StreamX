package com.yourapp.iptv.ui.component.organization

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.ui.presentation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

private data class IndexedPreview(val preview: OrganizedChannels, val labels: Map<FolderLabel, String>,
    val locale: String, val index: OrganizationSearchIndex)
private data class SearchSnapshot(val indexed: IndexedPreview, val query: String, val folder: String?,
    val review: Boolean, val rows: OrganizationSearchRows)

@Composable
internal fun organizationSearchRows(preview: OrganizedChannels, query: String, folder: String?, reviewOnly: Boolean): OrganizationSearchRows? {
    val labels = organizationFolderLabels()
    val locale = LocalConfiguration.current.locales[0].toLanguageTag()
    val indexed by produceState<IndexedPreview?>(null, preview, labels, locale) {
        value = withContext(Dispatchers.Default) {
            IndexedPreview(preview, labels, locale, OrganizationSearchIndex(preview, labels, Locale.forLanguageTag(locale)))
        }
    }
    val current = indexed?.takeIf { it.preview === preview && it.labels == labels && it.locale == locale }
    val snapshot by produceState<SearchSnapshot?>(null, current, query, folder, reviewOnly) {
        if (current == null) { value = null; return@produceState }
        if (query.isNotBlank()) delay(180)
        value = withContext(Dispatchers.Default) {
            SearchSnapshot(current, query, folder, reviewOnly, current.index.find(query, folder, reviewOnly))
        }
    }
    return snapshot?.takeIf { it.indexed === current && it.query == query && it.folder == folder && it.review == reviewOnly }?.rows
}