package com.yourapp.iptv.data.backup

import com.yourapp.iptv.domain.model.*

/** Bound JSON construction before toString/toByteArray can allocate a large intermediate. */
internal class BackupSizeBudget(private val limit: Int) {
    private var used = 1024L
    fun text(value: String, overhead: Int = 64) {
        used += overhead
        var index = 0
        while (index < value.length) {
            val c = value[index++]
            used += when {
                c == '"' || c == '\\' -> 2
                c.code < 32 || c in "<>&='\u2028\u2029" -> 6
                c.code < 128 -> 1
                c.code < 2048 -> 2
                c.isHighSurrogate() && index < value.length && value[index].isLowSurrogate() -> { index++; 4 }
                else -> 3
            }
            if (used > limit) throw BackupException(BackupProblem.TOO_LARGE)
        }
        if (used > limit) throw BackupException(BackupProblem.TOO_LARGE)
    }
    companion object {
        fun payload(value: BackupPayload) {
            if (value.favorites.size > BackupLimits.FAVORITES) throw BackupException(BackupProblem.TOO_LARGE)
            val budget = BackupSizeBudget(BackupLimits.PLAIN)
            value.subscriptions.entries.forEach { entry ->
                budget.text(entry.id); budget.text(entry.name)
                val s = entry.settings
                listOf(s.m3uUrl,s.xtreamServer,s.xtreamUsername,s.xtreamPassword,s.epgUrl).forEach { budget.text(it) }
            }
            value.areas.values.forEach { rows -> rows.forEach { (name,item) -> budget.text(name);budget.text(item.text,128) } }
            value.favorites.forEach { budget.text(it.source);budget.text(it.channel) }
        }
        fun snapshot(value: BackupSnapshot, limit: Int = BackupLimits.JOURNAL) {
            val budget = BackupSizeBudget(limit)
            value.stores.values.forEach { prefs -> prefs.asMap().forEach { (key,item) ->
                budget.text(key.name)
                if (item is Set<*>) item.forEach { budget.text(it as String) } else budget.text(item.toString(),128)
            } }
            value.favorites.forEach { budget.text(it.source);budget.text(it.channel) }
            value.legacyFavorites.forEach { budget.text(it) }
        }
    }
}