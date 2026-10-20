package com.yourapp.iptv.ui.screen.appearance

import androidx.compose.ui.res.stringResource
import com.yourapp.iptv.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.PlayerAppearance
import com.yourapp.iptv.domain.model.PlayerPanelShade
import com.yourapp.iptv.domain.model.PlayerTextSize

@Composable
internal fun PlayerAppearanceForm(value: PlayerAppearance, enabled: Boolean, onChange: (PlayerAppearance) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AppearanceChoice(stringResource(R.string.ui_text_size), value.textSize,
            listOf(PlayerTextSize.SMALL to stringResource(R.string.ui_small), PlayerTextSize.NORMAL to stringResource(R.string.ui_normal), PlayerTextSize.LARGE to stringResource(R.string.ui_large)), enabled) {
            onChange(value.copy(textSize = it))
        }
        AppearanceChoice(stringResource(R.string.ui_panel_background), value.panelShade,
            listOf(PlayerPanelShade.SOFT to stringResource(R.string.ui_shade_soft), PlayerPanelShade.BALANCED to stringResource(R.string.ui_shade_balanced), PlayerPanelShade.SOLID to stringResource(R.string.ui_shade_solid)), enabled) {
            onChange(value.copy(panelShade = it))
        }
        AppearanceChoice(stringResource(R.string.ui_autohide), value.displaySeconds,
            listOf(3 to stringResource(R.string.ui_seconds_three), 5 to stringResource(R.string.ui_seconds_five), 8 to stringResource(R.string.ui_seconds_eight)), enabled) {
            onChange(value.copy(displaySeconds = it))
        }
        AppearanceToggle(stringResource(R.string.ui_show_logo), value.showLogo, enabled) { onChange(value.copy(showLogo = it)) }
        AppearanceToggle(stringResource(R.string.ui_show_group), value.showGroup, enabled) { onChange(value.copy(showGroup = it)) }
        AppearanceToggle(stringResource(R.string.ui_show_counter), value.showCounter, enabled) { onChange(value.copy(showCounter = it)) }
        AppearanceToggle(stringResource(R.string.ui_show_next), value.showNext, enabled) { onChange(value.copy(showNext = it)) }
        AppearanceToggle(stringResource(R.string.ui_show_description), value.showDescription,
            enabled) { onChange(value.copy(showDescription = it)) }
        AppearanceToggle(stringResource(R.string.ui_show_zap), value.showChannelButtons, enabled) {
            onChange(value.copy(showChannelButtons = it))
        }
        AppearanceToggle(stringResource(R.string.ui_show_hints), value.showHints, enabled) { onChange(value.copy(showHints = it)) }
    }
}