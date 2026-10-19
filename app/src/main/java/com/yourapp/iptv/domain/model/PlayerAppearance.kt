package com.yourapp.iptv.domain.model

enum class PlayerInfoStyle { CARDS }
enum class PlayerTextSize(val scale: Float) { SMALL(0.9f), NORMAL(1f), LARGE(1.15f) }
enum class PlayerPanelShade(val alpha: Float) { SOFT(0.65f), BALANCED(0.8f), SOLID(0.94f) }

data class PlayerAppearance(
    val style: PlayerInfoStyle = PlayerInfoStyle.CARDS,
    val textSize: PlayerTextSize = PlayerTextSize.NORMAL,
    val panelShade: PlayerPanelShade = PlayerPanelShade.BALANCED,
    val displaySeconds: Int = 5,
    val showLogo: Boolean = true,
    val showGroup: Boolean = true,
    val showCounter: Boolean = true,
    val showNext: Boolean = true,
    val showDescription: Boolean = true,
    val showChannelButtons: Boolean = true,
    val showHints: Boolean = false
) {
    fun normalized() = copy(displaySeconds = displaySeconds.takeIf { it in setOf(3, 5, 8) } ?: 5)
    val timeoutMillis: Long get() = normalized().displaySeconds * 1000L
}