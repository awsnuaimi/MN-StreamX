package com.yourapp.iptv.ui.presentation

import com.yourapp.iptv.domain.organization.*
import java.text.Collator
import java.util.Locale

internal object FolderDisplayOrder {
    fun folders(rows: List<ChannelFolder>, tree: OrganizationTree, names: Map<String, String>, locale: Locale): List<ChannelFolder> {
        if (tree.folderSort == FolderSort.SAVED) return rows
        val collator = Collator.getInstance(locale)
        val keys = rows.associate { it.id to collator.getCollationKey(names[it.id].orEmpty()) }
        return rows.sortedWith { a, b ->
            val count = if (tree.folderSort == FolderSort.COUNT) (tree.counts[b.id] ?: 0).compareTo(tree.counts[a.id] ?: 0) else 0
            count.takeIf { it != 0 } ?: keys.getValue(a.id).compareTo(keys.getValue(b.id)).takeIf { it != 0 }
                ?: a.order.compareTo(b.order).takeIf { it != 0 } ?: a.id.compareTo(b.id)
        }
    }

    fun groups(rows: List<SourceGroup>, mode: FolderSort, locale: Locale): List<SourceGroup> {
        if (mode == FolderSort.SAVED) return rows
        val collator = Collator.getInstance(locale)
        val keys = rows.associate { it.key to collator.getCollationKey(it.name.orEmpty()) }
        return rows.sortedWith { a, b ->
            val count = if (mode == FolderSort.COUNT) b.count.compareTo(a.count) else 0
            count.takeIf { it != 0 } ?: keys.getValue(a.key).compareTo(keys.getValue(b.key)).takeIf { it != 0 }
                ?: a.key.compareTo(b.key)
        }
    }
}