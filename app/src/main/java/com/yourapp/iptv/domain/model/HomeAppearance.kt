package com.yourapp.iptv.domain.model

enum class HomeLanguage { ARABIC, ENGLISH, GERMAN }
enum class SidebarSide { AUTO, RIGHT, LEFT }
data class HomeAppearance(
    val language: HomeLanguage = HomeLanguage.ARABIC,
    val sidebarSide: SidebarSide = SidebarSide.AUTO,
    val expandedSidebar: Boolean = false,
    val columns: Int = 3,
    val restoreBrowseSession: Boolean = true
) {
    val sidebarOnRight: Boolean get() = sidebarSide == SidebarSide.RIGHT ||
        (sidebarSide == SidebarSide.AUTO && language == HomeLanguage.ARABIC)
    val isRtl: Boolean get() = language == HomeLanguage.ARABIC
    fun normalized() = copy(columns = columns.takeIf { it in 2..4 } ?: 3)
}