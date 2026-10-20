package com.yourapp.iptv.domain.organization

import com.yourapp.iptv.domain.model.Channel

class OrganizationTree(val enabled: Boolean = false, val folders: List<ChannelFolder> = emptyList(),
    val counts: Map<String, Int> = emptyMap(), val readFailed: Boolean = false,
    val folderSort: FolderSort = FolderSort.SAVED) {
    private val byId = folders.associateBy { it.id }
    private val children = folders.groupBy { it.parentId }.mapValues { (_, rows) ->
        rows.sortedWith(compareBy<ChannelFolder> { it.order }.thenBy { it.id })
    }
    fun folder(id: String?) = byId[id]
    fun children(id: String?) = children[id].orEmpty()
    fun path(id: String): List<ChannelFolder> {
        val path = mutableListOf<ChannelFolder>(); val seen = mutableSetOf<String>()
        var current = folder(id)
        while (current != null && seen.add(current.id) && path.size < 6) {
            path.add(current); current = folder(current.parentId)
        }
        return path.asReversed()
    }
    fun orderedFolders(): List<ChannelFolder> = buildList {
        fun append(parent: String?, depth: Int) {
            if (depth >= 6) return
            children(parent).forEach { folder -> add(folder); append(folder.id, depth + 1) }
        }
        append(null, 0)
    }
    fun withCounts(channels: List<Channel>): OrganizationTree {
        val values = mutableMapOf<String, Int>()
        channels.forEach { c -> c.folderIds.forEach { values[it] = (values[it] ?: 0) + 1 } }
        return OrganizationTree(enabled, folders, values, readFailed, folderSort)
    }
}

data class OrganizedChannels(val channels: List<Channel>, val tree: OrganizationTree,
    val assignments: Map<String, FolderAssignment>, val groups: List<SourceGroup>) {
    fun resolveFilter(value: String?): String? {
        if (value == null) return null
        val folderId = FolderFilter.id(value)
        if (folderId != null && tree.enabled && tree.folder(folderId) != null) return value
        val members = channels.filter { it.group == value }
        if (!tree.enabled) return value.takeIf { members.isNotEmpty() }
        if (members.isEmpty()) return null
        val shared = members.first().folderIds.takeWhile { id -> members.all { id in it.folderIds } }
        return shared.lastOrNull()?.let(FolderFilter::of)
    }
}

object FolderEdits {
    fun assignGroups(config: OrganizationConfig, available: Set<String>, selected: Set<String>, destination: String?): OrganizationConfig {
        if (selected.isEmpty()) return config
        require(selected.all { it in available })
        require(destination == null || config.folders.any { it.id == destination })
        return validate(config.copy(groupOverrides = config.groupOverrides.toMutableMap().apply {
            selected.forEach { key -> if (destination == null) remove(key) else put(key, destination) }
        }))
    }
    fun validate(config: OrganizationConfig): OrganizationConfig {
        require(config.revision >= 0 && config.revision < Long.MAX_VALUE)
        require(config.folders.size <= 10000 && config.folders.map { it.id }.distinct().size == config.folders.size)
        val folders = config.folders.associateBy { it.id }
        config.folders.forEach { folder ->
            require(folder.id.matches(Regex("[A-Za-z0-9:_-]{1,96}")))
            require(folder.name.length <= 80 && (folder.name.isNotBlank() || folder.label != null))
            val seen = mutableSetOf<String>(); var current: ChannelFolder? = folder
            while (current != null) {
                require(seen.add(current.id) && seen.size <= 6)
                current = current.parentId?.let { requireNotNull(folders[it]) }
            }
        }
        require(config.groupOverrides.size <= 10000 && config.channelOverrides.size <= 50000)
        require(config.groupOverrides.keys.all { it.length in 1..256 } && config.channelOverrides.keys.all { it.length in 1..512 })
        require((config.groupOverrides.values + config.channelOverrides.values).all { it in folders })
        return config
    }
    fun upsert(config: OrganizationConfig, id: String, name: String, parent: String?): OrganizationConfig {
        val existing = config.folders.firstOrNull { it.id == id }
        val title = name.trim(); require(title.isNotEmpty())
        val order = if (existing != null && existing.parentId == parent) existing.order
            else (config.folders.filter { it.parentId == parent }.maxOfOrNull { it.order } ?: -1) + 1
        val folder = ChannelFolder(id, parent, existing?.label, title, order)
        return validate(config.copy(folders = config.folders.filterNot { it.id == id } + folder))
    }
    fun move(config: OrganizationConfig, id: String, direction: Int): OrganizationConfig {
        val folder = config.folders.firstOrNull { it.id == id } ?: return config
        val siblings = config.folders.filter { it.parentId == folder.parentId }.sortedWith(compareBy<ChannelFolder> { it.order }.thenBy { it.id }).toMutableList()
        val from = siblings.indexOfFirst { it.id == id }; val to = from + direction
        if (to !in siblings.indices) return config
        java.util.Collections.swap(siblings, from, to)
        val orders = siblings.mapIndexed { i, item -> item.id to i }.toMap()
        return config.copy(folders = config.folders.map { orders[it.id]?.let { order -> it.copy(order = order) } ?: it })
    }
}