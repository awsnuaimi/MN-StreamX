package com.yourapp.iptv.ui.screen.home

/** Dimensions in dp; cards include a 20:13 artwork/caption area as in the Home reference. */
internal data class HomeDashboardMetrics(
    val heroPadding: Int,
    val sectionSpacing: Int,
    val actionWidth: Int,
    val actionHeight: Int,
    val channelWidth: Int,
    val channelAspectRatio: Float,
    val logoSize: Int,
    val compact: Boolean
)

internal object HomeDashboardLayout {
    fun forWidth(widthDp: Int): HomeDashboardMetrics = when {
        widthDp < 560 -> HomeDashboardMetrics(
            heroPadding = 14, sectionSpacing = 12,
            actionWidth = 132, actionHeight = 64,
            channelWidth = 192, channelAspectRatio = 20f / 13f,
            logoSize = 42, compact = true
        )
        widthDp < 920 -> HomeDashboardMetrics(
            heroPadding = 18, sectionSpacing = 16,
            actionWidth = 160, actionHeight = 76,
            channelWidth = 192, channelAspectRatio = 20f / 13f,
            logoSize = 50, compact = false
        )
        else -> HomeDashboardMetrics(
            heroPadding = 24, sectionSpacing = 20,
            actionWidth = 186, actionHeight = 88,
            channelWidth = 232, channelAspectRatio = 20f / 13f,
            logoSize = 58, compact = false
        )
    }
}