package com.yourapp.iptv.domain.organization

import com.yourapp.iptv.domain.model.Channel

object ChannelOrganizer {
    fun project(input: List<Channel>, config: OrganizationConfig): OrganizedChannels =
        project(input, config) {}

    internal fun project(
        input: List<Channel>,
        config: OrganizationConfig,
        checkpoint: () -> Unit
    ): OrganizedChannels {
        checkpoint()
        val channels = input.distinctBy { it.id }

        if (!config.enabled) return OrganizedChannels(
            channels.mapIndexed { i, channel ->
                if ((i and 127) == 0) checkpoint()
                if (channel.folderIds.isEmpty()) channel
                else channel.copy(folderIds = emptyList())
            },
            OrganizationTree(),
            emptyMap(),
            emptyList()
        )

        val folders = (OrganizationDefaults.folders() + config.folders)
            .associateBy { it.id }.toMutableMap()

        FolderEdits.validate(FolderResolver.validationCopy(config, folders))

        val nextOrders = folders.values.groupBy { it.parentId }
            .mapValues { (_, rows) ->
                maxOf(1000, rows.maxOf { it.order } + 1)
            }.toMutableMap()

        val keys = mutableMapOf<String?, String>()
        val sourceGroups = channels.groupBy { channel ->
            keys.getOrPut(channel.group) {
                OrganizationNames.groupKey(channel.group)
            }
        }

        val detected = mutableMapOf<String, String?>()
        val classifier = AutomaticClassifier { group ->
            val key = keys.getValue(group)
            if (key !in detected) {
                checkpoint()
                detected[key] = ChannelLanguageDetector.detect(
                    sourceGroups.getValue(key).map { it.name }
                )
            }
            detected[key]
        }

        val rootTree = OrganizationTree(true, folders.values.toList())
        val rootDepths = listOf(
            "arabic", "bein", "german", "polish", "unclassified"
        ).associateWith { rootTree.path(it).size }

        val names = channels.mapIndexed { i, channel ->
            if ((i and 127) == 0) checkpoint()
            channel.id to OrganizationNames.normalize(channel.name)
        }.toMap()

        val destinations = mutableMapOf<Pair<String, String>, String>()

        val assignments = channels.mapIndexed { i, channel ->
            if ((i and 127) == 0) checkpoint()

            val key = keys.getValue(channel.group)
            val manual = FolderResolver.manual(channel.id, key, config, folders)

            val assignment = manual ?: run {
                val auto = classifier.classify(channel.group, channel.name)

                val id = if (auto.root == "bein") {
                    "bein"
                } else {
                    destinations.getOrPut(auto.root to key) {
                        val sourceId = "src-" +
                            OrganizationNames.digest(auto.root + key).take(32)

                        if (sourceId !in folders &&
                            (rootDepths[auto.root] ?: 6) >= 6
                        ) {
                            auto.root
                        } else {
                            folders.getOrPut(sourceId) {
                                val order = nextOrders[auto.root] ?: 1000
                                nextOrders[auto.root] = order + 1
                                val original = sourceGroups.getValue(key).first().group

                                ChannelFolder(
                                    sourceId,
                                    auto.root,
                                    if (original.isNullOrBlank()) FolderLabel.OTHER else null,
                                    original.orEmpty().take(80),
                                    order
                                )
                            }
                            sourceId
                        }
                    }
                }

                FolderAssignment(id, auto.reason)
            }

            channel.id to assignment
        }.toMap()

        checkpoint()

        val tree = OrganizationTree(
            true,
            folders.values.toList(),
            folderSort = config.folderSort
        )

        val siblingOrder = mutableMapOf<String, Int>()
        folders.values.map { it.parentId }.distinct().forEach { parent ->
            tree.children(parent).forEachIndexed { index, folder ->
                siblingOrder[folder.id] = index
            }
        }

        val paths = mutableMapOf<String, List<String>>()
        val projected = channels.mapIndexed { i, channel ->
            if ((i and 127) == 0) checkpoint()

            val assignment = assignments.getValue(channel.id)
            channel.copy(
                folderIds = paths.getOrPut(assignment.folderId) {
                    FolderResolver.path(assignment, tree)
                }
            )
        }

        val sorted = OrganizationChannelOrder.sort(projected, names, siblingOrder)
        checkpoint()

        val groups = sourceGroups.map { (key, members) ->
            SourceGroup(
                key,
                members.first().group,
                members.size,
                members.map { assignments.getValue(it.id).folderId }.toSet()
            )
        }.sortedBy { OrganizationNames.normalize(it.name.orEmpty()) }

        return OrganizedChannels(sorted, tree.withCounts(sorted), assignments, groups)
    }
}