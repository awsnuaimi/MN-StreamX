package com.yourapp.iptv.domain.parental

import com.yourapp.iptv.domain.organization.*

object ParentalProjection {
    /** Hide empty names as well as posters/titles; never edit the saved organization configuration. */
    fun prune(value: OrganizedChannels, state: ParentalState): OrganizedChannels {
        if (!state.contentRestricted) return value
        val tree = value.tree
        return value.copy(tree = OrganizationTree(tree.enabled,
            tree.folders.filter { (tree.counts[it.id] ?: 0) > 0 },tree.counts,tree.readFailed,tree.folderSort))
    }
}