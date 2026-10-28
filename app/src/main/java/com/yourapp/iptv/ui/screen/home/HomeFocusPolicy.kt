package com.yourapp.iptv.ui.screen.home

/** Persist only a stable enum name; never persist Compose focus nodes. */
internal object HomeFocusPolicy {
    val rail = listOf(HomeSection.DASHBOARD, HomeSection.LIVE, HomeSection.MOVIES, HomeSection.SERIES, HomeSection.FAVORITES, HomeSection.MORE, HomeSection.SETTINGS)
    fun railTarget(section: HomeSection): HomeSection = section.takeIf { it in rail } ?: HomeSection.MORE

    fun restore(savedName: String?, loading: Boolean, organizationReady: Boolean): HomeSection {
        val saved = HomeSection.entries.firstOrNull { it.name == savedName } ?: HomeSection.DASHBOARD
        return when {
            saved == HomeSection.REFRESH && loading -> HomeSection.DASHBOARD
            saved == HomeSection.ORGANIZE && !organizationReady -> HomeSection.DASHBOARD
            else -> saved
        }
    }
}