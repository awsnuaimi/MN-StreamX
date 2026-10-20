package com.yourapp.iptv.ui.presentation

import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.organization.*
import java.util.Locale

internal data class OrganizationSearchRows(val groups: List<SourceGroup>, val channels: List<Channel>,
    val folders: List<ChannelFolder>, val paths: Map<String, String>)

/** Built once per preview/language on a background dispatcher; queries reuse normalized text. */
internal class OrganizationSearchIndex(val preview: OrganizedChannels, labels: Map<FolderLabel, String>, locale: Locale) {
    private data class Entry<T>(val value: T, val text: String, val folders: Collection<String>, val review: Boolean)
    val names = preview.tree.folders.associate { folder -> folder.id to
        folder.name.takeIf { it.isNotBlank() }.orEmpty().ifEmpty { labels[folder.label ?: FolderLabel.OTHER].orEmpty() } }
    val paths = preview.tree.folders.associate { it.id to preview.tree.path(it.id).joinToString(" › ") { f -> names[f.id].orEmpty() } }
    private val groups = FolderDisplayOrder.groups(preview.groups, preview.tree.folderSort, locale).map { group ->
        val ids = group.folderIds.flatMap { preview.tree.path(it).map { f -> f.id } }.toSet()
        Entry(group, OrganizationNames.normalize(group.name.orEmpty() + " " + group.folderIds.joinToString(" ") { paths[it].orEmpty() }),
            ids, "unclassified" in ids)
    }
    private val channels = preview.channels.map { channel ->
        Entry(channel, OrganizationNames.normalize(channel.name + " " + channel.group.orEmpty() + " " + paths[channel.folderIds.lastOrNull()].orEmpty()),
            channel.folderIds, "unclassified" in channel.folderIds)
    }
    private val folders = preview.tree.orderedFolders().map { folder ->
        Entry(folder, OrganizationNames.normalize(paths[folder.id].orEmpty()), emptySet(), false)
    }

    fun find(query: String, folder: String? = null, reviewOnly: Boolean = false): OrganizationSearchRows {
        val words = OrganizationNames.normalize(query).split(' ').filter { it.isNotEmpty() }
        fun <T> matches(row: Entry<T>) = words.all { it in row.text } &&
            (!reviewOnly || row.review) && (folder == null || folder in row.folders)
        return OrganizationSearchRows(groups.filter { matches(it) }.map { it.value }, channels.filter { matches(it) }.map { it.value },
            folders.filter { row -> words.all { it in row.text } }.map { it.value }, paths)
    }
}