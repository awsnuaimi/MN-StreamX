package com.yourapp.iptv.domain.organization

data class MissingGroupRule(
    val key: String,
    val folderId: String,
    val name: String?
)

data class RecoveryCandidate(
    val group: SourceGroup,
    val score: Int,
    val available: Boolean
)

data class GroupRelinkPlan(
    val before: OrganizationConfig,
    val after: OrganizationConfig,
    val oldKey: String,
    val newKey: String,
    val folderId: String
)

data class GroupRelinkUndo(
    val sourceKey: String,
    val oldKey: String,
    val newKey: String,
    val folderId: String
)

data class GroupRecoveryPreview(
    val oldKey: String,
    val newName: String?,
    val folderId: String,
    val total: Int,
    val changed: Int,
    val protectedChannels: Int
)

object OrganizationRecovery {
    fun missing(
        config: OrganizationConfig,
        groups: List<SourceGroup>
    ): List<MissingGroupRule> {
        if (!config.enabled || groups.isEmpty()) return emptyList()

        val present = groups.mapTo(hashSetOf()) { it.key }
        val absent = config.groupOverrides.keys.filterTo(hashSetOf()) {
            it !in present
        }

        if (absent.isEmpty()) return emptyList()

        val names = mutableMapOf<String, String>()
        config.folders.forEach { folder ->
            if (folder.id.startsWith("src-") && folder.name.isNotBlank()) {
                val key = OrganizationNames.groupKey(folder.name)
                if (key in absent) names.putIfAbsent(key, folder.name)
            }
        }

        return absent.sorted().map {
            MissingGroupRule(it, config.groupOverrides.getValue(it), names[it])
        }
    }

    fun candidates(
        rule: MissingGroupRule,
        config: OrganizationConfig,
        groups: List<SourceGroup>,
        query: String
    ): List<RecoveryCandidate> {
        val search = OrganizationNames.normalizeMatch(query)
            .split(' ').filter { it.isNotBlank() }

        val previous = tokens(rule.name.orEmpty())

        return groups.asSequence().map { group ->
            val text = OrganizationNames.normalizeMatch(group.name.orEmpty())
            val words = tokens(group.name.orEmpty())
            val union = (previous + words).size

            val score = if (previous.isEmpty() || union == 0) {
                0
            } else {
                100 * previous.intersect(words).size / union
            }

            Triple(group, text, score)
        }.filter { (_, text, _) ->
            search.all { it in text }
        }.sortedWith(
            compareByDescending<Triple<SourceGroup, String, Int>> { it.third }
                .thenBy { it.second }
                .thenBy { it.first.key }
        ).map {
            RecoveryCandidate(
                it.first,
                it.third,
                it.first.key !in config.groupOverrides
            )
        }.toList()
    }

    fun plan(
        config: OrganizationConfig,
        groups: List<SourceGroup>,
        oldKey: String,
        newKey: String
    ): GroupRelinkPlan {
        require(config.enabled && groups.isNotEmpty())

        val present = groups.mapTo(hashSetOf()) { it.key }
        require(oldKey !in present)
        require(newKey in present)
        require(newKey !in config.groupOverrides)

        val folder = requireNotNull(config.groupOverrides[oldKey])
        require(config.folders.any { it.id == folder })

        val updated = config.copy(
            groupOverrides = config.groupOverrides.toMutableMap().apply {
                remove(oldKey)
                put(newKey, folder)
            }
        )

        return GroupRelinkPlan(
            config,
            FolderEdits.validate(updated),
            oldKey,
            newKey,
            folder
        )
    }

    fun undo(
        config: OrganizationConfig,
        token: GroupRelinkUndo
    ): OrganizationConfig {
        require(config.sourceKey == token.sourceKey)
        require(token.oldKey !in config.groupOverrides)
        require(config.groupOverrides[token.newKey] == token.folderId)

        return FolderEdits.validate(
            config.copy(
                groupOverrides = config.groupOverrides.toMutableMap().apply {
                    remove(token.newKey)
                    put(token.oldKey, token.folderId)
                }
            )
        )
    }

    private fun tokens(name: String): Set<String> =
        if (name.isBlank()) {
            emptySet()
        } else {
            OrganizationNames.normalizeMatch(
                OrganizationNames.canonicalGroup(name)
            ).split(' ').filter { it.isNotBlank() }.toSet()
        }
}