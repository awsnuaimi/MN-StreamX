package com.yourapp.iptv.domain.organization

import com.yourapp.iptv.domain.model.Channel

object ChannelOrganizer {
    fun project(input: List<Channel>, config: OrganizationConfig): OrganizedChannels {
        val channels = input.distinctBy { it.id }
        if (!config.enabled) return OrganizedChannels(channels.map { if (it.folderIds.isEmpty()) it else it.copy(folderIds = emptyList()) },
            OrganizationTree(), emptyMap(), emptyList())
        FolderEdits.validate(config)
        val folders = (OrganizationDefaults.folders() + config.folders).associateBy { it.id }.toMutableMap()
        val nextOrders = folders.values.groupBy { it.parentId }.mapValues { (_, rows) -> maxOf(1000, rows.maxOf { it.order } + 1) }.toMutableMap()
        val classifier = AutomaticClassifier()
        val rootDepths = listOf("arabic", "bein", "german", "polish", "unclassified")
            .associateWith { OrganizationTree(true, folders.values.toList()).path(it).size }
        val keys = mutableMapOf<String?, String>()
        val names = channels.associate { it.id to OrganizationNames.normalize(it.name) }
        val assignments = channels.associate { channel ->
            val key = keys.getOrPut(channel.group) { OrganizationNames.groupKey(channel.group) }
            val direct = config.channelOverrides[channel.id]
            val group = config.groupOverrides[key]
            val assignment = when {
                direct != null -> FolderAssignment(direct, AssignmentReason.CHANNEL_RULE)
                group != null -> FolderAssignment(group, AssignmentReason.GROUP_RULE)
                else -> {
                    val auto = classifier.classify(channel.group, names.getValue(channel.id))
                    val leaf = if (auto.leaf != null) OrganizationDefaults.child(auto.root, auto.leaf) else {
                        val id = "src-" + OrganizationNames.digest(auto.root + key).take(32)
                        if (id !in folders && rootDepths.getValue(auto.root) >= 6) auto.root else {
                        folders.getOrPut(id) {
                            val order = nextOrders[auto.root] ?: 1000
                            nextOrders[auto.root] = order + 1
                            ChannelFolder(id, auto.root, if (channel.group.isNullOrBlank()) FolderLabel.OTHER else null,
                                channel.group.orEmpty().trim().take(80), order)
                        }
                        id
                        }
                    }
                    FolderAssignment(leaf, auto.reason)
                }
            }
            channel.id to assignment
        }
        val tree = OrganizationTree(true, folders.values.toList(), folderSort = config.folderSort)
        val siblingOrder = mutableMapOf<String, Int>()
        folders.values.map { it.parentId }.distinct().forEach { parent ->
            tree.children(parent).forEachIndexed { index, folder -> siblingOrder[folder.id] = index }
        }
        val paths = folders.keys.associateWith { id -> tree.path(id).map { it.id } }
        val projected = channels.map { channel ->
            channel.copy(folderIds = paths.getValue(assignments.getValue(channel.id).folderId))
        }
        val sorted = OrganizationChannelOrder.sort(projected, names, siblingOrder)
        val groups = channels.groupBy { keys.getValue(it.group) }.map { (key, members) ->
            SourceGroup(key, members.first().group, members.size, members.map { assignments.getValue(it.id).folderId }.toSet())
        }.sortedBy { OrganizationNames.normalize(it.name.orEmpty()) }
        return OrganizedChannels(sorted, tree.withCounts(sorted), assignments, groups)
    }
}