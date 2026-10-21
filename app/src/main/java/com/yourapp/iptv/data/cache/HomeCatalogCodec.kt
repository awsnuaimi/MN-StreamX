package com.yourapp.iptv.data.cache

import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.HomeStartupSnapshot
import com.yourapp.iptv.domain.organization.*
import java.io.DataInputStream
import java.io.DataOutputStream

/** Materialized catalog, not user-owned data. Bump VERSION when classification semantics change. */
internal object HomeCatalogCodec {
    private const val MAGIC = 0x4D4E5843
    private const val VERSION = 24
    private const val MAX_CHANNELS = 30_000
    private const val MAX_FOLDERS = 35_000
    private const val MAX_STRING = 1024 * 1024

    fun write(out: DataOutputStream, fingerprint: String, config: OrganizationConfig,
        catalog: OrganizedChannels, refreshedAt: Long, checkpoint: () -> Unit) {
        require(catalog.channels.size in 1..MAX_CHANNELS && catalog.tree.folders.size <= MAX_FOLDERS)
        out.writeInt(MAGIC); out.writeInt(VERSION)
        out.text(fingerprint); out.text(HomeCatalogKey.of(config)); out.writeLong(refreshedAt)
        out.writeBoolean(catalog.tree.enabled)
        val folders = catalog.tree.folders
        val folderIndex = folders.withIndex().associate { it.value.id to it.index }
        out.writeInt(folders.size)
        folders.forEach { folder ->
            checkpoint()
            out.text(folder.id); out.text(folder.parentId); out.writeInt(folder.label?.ordinal ?: -1)
            out.text(folder.name); out.writeInt(folder.order)
        }
        val groupNames = catalog.channels.map { it.group }.distinct()
        val groupIndex = groupNames.withIndex().associate { it.value to it.index }
        out.writeInt(groupNames.size); groupNames.forEach { out.text(it) }
        out.writeInt(catalog.channels.size)
        catalog.channels.forEachIndexed { index, channel ->
            if ((index and 127) == 0) checkpoint()
            out.text(channel.id); out.text(channel.name); out.text(channel.logoUrl); out.text(channel.streamUrl)
            out.writeInt(groupIndex.getValue(channel.group))
            val assignment = catalog.assignments[channel.id]
            out.writeInt(assignment?.let { folderIndex.getValue(it.folderId) } ?: -1)
            out.writeInt(assignment?.reason?.ordinal ?: -1)
        }
        out.writeInt(catalog.groups.size)
        catalog.groups.forEach { group ->
            checkpoint()
            out.text(group.key); out.text(group.name); out.writeInt(group.count)
            out.writeInt(group.folderIds.size)
            group.folderIds.forEach { out.writeInt(folderIndex.getValue(it)) }
        }
    }

    fun read(input: DataInputStream, fingerprint: String, config: OrganizationConfig,
        checkpoint: () -> Unit): HomeStartupSnapshot {
        require(input.readInt() == MAGIC && input.readInt() == VERSION)
        require(input.text() == fingerprint && input.text() == HomeCatalogKey.of(config))
        val refreshedAt = input.readLong().also { require(it >= 0) }
        val enabled = input.readBoolean().also { require(it == config.enabled) }
        val folders = List(input.count(MAX_FOLDERS)) {
            checkpoint()
            val id = input.requiredText(); val parent = input.text(); val label = input.readInt()
            require(label in -1 until FolderLabel.entries.size)
            ChannelFolder(id, parent, FolderLabel.entries.getOrNull(label), input.requiredText(),
                input.readInt().also { require(it >= 0) })
        }
        require(folders.map { it.id }.distinct().size == folders.size)
        val topology = OrganizationTree(enabled, folders, folderSort = config.folderSort)
        folders.forEach {
            require(it.parentId == null || topology.folder(it.parentId) != null)
            require(topology.path(it.id).first().parentId == null)
        }
        val names = List(input.count(MAX_CHANNELS)) { input.text() }
        val assignments = HashMap<String, FolderAssignment>()
        val paths = HashMap<String, List<String>>()
        val seen = HashSet<String>()
        val channels = List(input.count(MAX_CHANNELS).also { require(it > 0) }) { index ->
            if ((index and 127) == 0) checkpoint()
            val id = input.requiredText().also { require(seen.add(it)) }
            val name = input.requiredText(); val logo = input.text(); val url = input.requiredText()
            val group = input.readInt().also { require(it in names.indices) }
            val folder = input.readInt(); val reason = input.readInt()
            val path = if (enabled) {
                require(folder in folders.indices && reason in AssignmentReason.entries.indices)
                val destination = folders[folder].id
                assignments[id] = FolderAssignment(destination, AssignmentReason.entries[reason])
                paths.getOrPut(destination) { topology.path(destination).map { it.id } }
            } else {
                require(folder == -1 && reason == -1)
                emptyList()
            }
            Channel(id, name, logo, url, names[group], folderIds = path)
        }
        val groups = List(input.count(MAX_CHANNELS)) {
            checkpoint()
            val key = input.requiredText(); val name = input.text(); val count = input.count(channels.size)
            val ids = List(input.count(folders.size)) {
                folders[input.readInt().also { require(it in folders.indices) }].id
            }.toSet()
            SourceGroup(key, name, count, ids)
        }
        return HomeStartupSnapshot(config,
            OrganizedChannels(channels, topology.withCounts(channels), assignments, groups), refreshedAt)
    }

    private fun DataOutputStream.text(value: String?) {
        if (value == null) { writeInt(-1); return }
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STRING)
        writeInt(bytes.size); write(bytes)
    }
    private fun DataInputStream.text(): String? {
        val size = readInt()
        if (size == -1) return null
        require(size in 0..MAX_STRING)
        val bytes = ByteArray(size); readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }
    private fun DataInputStream.requiredText() = requireNotNull(text())
    private fun DataInputStream.count(limit: Int) = readInt().also { require(it in 0..limit) }
}