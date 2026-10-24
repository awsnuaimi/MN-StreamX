package com.yourapp.iptv.ui.screen.home

/** TV dashboard dimensions in dp, derived only from the available content width. */
internal data class HomeDashboardMetrics(
    val heroPadding: Int,
    val sectionSpacing: Int,
    val actionWidth: Int,
    val actionHeight: Int,
    val channelWidth: Int,
    val channelHeight: Int,
    val logoSize: Int,
    val compact: Boolean
)

internal object HomeDashboardLayout {
    fun forWidth(widthDp: Int): HomeDashboardMetrics = when {
        widthDp < 560 -> HomeDashboardMetrics(
            heroPadding = 14, sectionSpacing = 12,
            actionWidth = 132, actionHeight = 64,
            channelWidth = 168, channelHeight = 92,
            logoSize = 42, compact = true
        )
        widthDp < 920 -> HomeDashboardMetrics(
            heroPadding = 18, sectionSpacing = 16,
            actionWidth = 160, actionHeight = 76,
            channelWidth = 206, channelHeight = 108,
            logoSize = 50, compact = false
        )
        else -> HomeDashboardMetrics(
            heroPadding = 24, sectionSpacing = 20,
            actionWidth = 186, actionHeight = 88,
            channelWidth = 240, channelHeight = 120,
            logoSize = 58, compact = false
        )
    }
}