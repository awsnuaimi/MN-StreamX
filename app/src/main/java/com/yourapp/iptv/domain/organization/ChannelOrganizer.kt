package com.yourapp.iptv.domain.organization

import com.yourapp.iptv.domain.model.Channel

object ChannelOrganizer {
    fun project(input: List<Channel>, config: OrganizationConfig): OrganizedChannels {
        val channels = input.distinctBy { it.id }
        if (!config.enabled) return OrganizedChannels(channels.map { if (it.folderIds.isEmpty()) it else it.copy(folderIds = emptyList()) },
            OrganizationTree(), emptyMap(), emptyList())
        val folders = (OrganizationDefaults.folders() + config.folders).associateBy { it.id }.toMutableMap()
        FolderEdits.validate(FolderResolver.validationCopy(config, folders))
        val nextOrders = folders.values.groupBy { it.parentId }
            .mapValues { (_, rows) -> maxOf(1000, rows.maxOf { it.order } + 1) }.toMutableMap()
        val keys = mutableMapOf<String?, String>()
        val sourceGroups = channels.groupBy { channel ->
            keys.getOrPut(channel.group) { OrganizationNames.groupKey(channel.group) }
        }
        val detected = mutableMapOf<String, String?>()
        val classifier = AutomaticClassifier { group ->
            val key = keys.getValue(group)
            // containsKey also caches null: unknown groups must not be rescanned per channel.
            if (key !in detected) detected[key] = ChannelLanguageDetector.detect(sourceGroups.getValue(key).map { it.name })
            detected[key]
        }
        val rootDepths = listOf("arabic", "bein", "german", "polish", "unclassified")
            .associateWith { OrganizationTree(true, folders.values.toList()).path(it).size }
        val names = channels.associate { it.id to OrganizationNames.normalize(it.name) }
        val assignments = channels.associate { channel ->
            val key = keys.getValue(channel.group)
            val manual = FolderResolver.manual(channel.id, key, config, folders)
            val assignment = manual ?: run {
                val auto = classifier.classify(channel.group, channel.name)
                val id = if (auto.root == "bein") "bein" else {
                    val sourceId = "src-" + OrganizationNames.digest(auto.root + key).take(32)
                    if (sourceId !in folders && (rootDepths[auto.root] ?: 6) >= 6) auto.root else {
                        folders.getOrPut(sourceId) {
                            val order = nextOrders[auto.root] ?: 1000
                            nextOrders[auto.root] = order + 1
                            val original = sourceGroups.getValue(key).first().group
                            ChannelFolder(sourceId, auto.root, if (original.isNullOrBlank()) FolderLabel.OTHER else null,
                                original.orEmpty().take(80), order)
                        }
                        sourceId
                    }
                }
                FolderAssignment(id, auto.reason)
            }
            channel.id to assignment
        }
        val tree = OrganizationTree(true, folders.values.toList(), folderSort = config.folderSort)
        val siblingOrder = mutableMapOf<String, Int>()
        folders.values.map { it.parentId }.distinct().forEach { parent ->
            tree.children(parent).forEachIndexed { index, folder -> siblingOrder[folder.id] = index }
        }
        val projected = channels.map { channel ->
            channel.copy(folderIds = FolderResolver.path(assignments.getValue(channel.id), tree))
        }
        val sorted = OrganizationChannelOrder.sort(projected, names, siblingOrder)
        val groups = sourceGroups.map { (key, members) ->
            SourceGroup(key, members.first().group, members.size, members.map {
                assignments.getValue(it.id).folderId
            }.toSet())
        }.sortedBy { OrganizationNames.normalize(it.name.orEmpty()) }
        return OrganizedChannels(sorted, tree.withCounts(sorted), assignments, groups)
    }
}