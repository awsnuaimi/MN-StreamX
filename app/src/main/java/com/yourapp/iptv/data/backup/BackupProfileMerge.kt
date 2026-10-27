package com.yourapp.iptv.data.backup

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.profile.*

internal object BackupProfileMerge {
    fun merge(current: UserProfiles, saved: List<UserProfile>, settings: Boolean): UserProfiles {
        UserProfiles(saved).validate()
        val rows = current.entries.toMutableList()
        saved.forEach { row ->
            val index = rows.indexOfFirst { it.id == row.id }
            if (index >= 0) {
                // Identity, kind and rules are always local. A backup cannot promote a child.
                if (settings && row.id != UserProfiles.DEFAULT_ID) rows[index] = rows[index].copy(
                    home = row.home,player = row.player,resumeAtStartup = row.resumeAtStartup)
            } else {
                if (rows.size >= UserProfiles.LIMIT) throw BackupException(BackupProblem.PROFILE_LIMIT)
                rows.add(row.copy(home = if (settings) row.home else HomeAppearance(profileId = row.id),
                    player = if (settings) row.player else PlayerAppearance(profileId = row.id),
                    resumeAtStartup = settings && row.resumeAtStartup,
                    rules = if (row.kind == ProfileKind.CHILD) UserProfiles.childRules() else com.yourapp.iptv.domain.parental.ParentalRules()))
            }
        }
        return current.copy(entries = rows,retired = current.retired - rows.map { it.id }.toSet(),revision = current.revision + 1).validate()
    }
}