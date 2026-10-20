package com.yourapp.iptv.domain.organization

/** Isolates stale override resolution from persisted identity and configuration validation. */
internal object FolderResolver {
    fun manual(channelId: String, groupKey: String, config: OrganizationConfig,
        available: Map<String, ChannelFolder>): FolderAssignment? {
        config.channelOverrides[channelId]?.takeIf { it in available }?.let {
            return FolderAssignment(it, AssignmentReason.CHANNEL_RULE)
        }
        config.groupOverrides[groupKey]?.takeIf { it in available }?.let {
            return FolderAssignment(it, AssignmentReason.GROUP_RULE)
        }
        return null
    }

    /** Validate folder topology but do not crash projection on a stale manual destination. */
    fun validationCopy(config: OrganizationConfig, available: Map<String, ChannelFolder>): OrganizationConfig =
        config.copy(groupOverrides = config.groupOverrides.filterValues { it in available },
            channelOverrides = config.channelOverrides.filterValues { it in available })

    fun path(assignment: FolderAssignment, tree: OrganizationTree): List<String> {
        val actual = tree.path(assignment.folderId).map { it.id }
        if (actual.isNotEmpty() && actual.last() == assignment.folderId) return actual
        val fallback = tree.path("unclassified").map { it.id }
        return fallback.ifEmpty { listOf("unclassified") }
    }
}