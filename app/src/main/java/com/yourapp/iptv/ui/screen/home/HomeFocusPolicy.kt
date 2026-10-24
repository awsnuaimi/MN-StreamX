package com.yourapp.iptv.ui.screen.home

/** Persist only a stable enum name; never persist Compose focus nodes. */
internal object HomeFocusPolicy {
    fun restore(savedName: String?, loading: Boolean, organizationReady: Boolean): HomeSection {
        val saved = HomeSection.entries.firstOrNull { it.name == savedName } ?: HomeSection.DASHBOARD
        return when {
            saved == HomeSection.REFRESH && loading -> HomeSection.DASHBOARD
            saved == HomeSection.ORGANIZE && !organizationReady -> HomeSection.DASHBOARD
            else -> saved
        }
    }
}