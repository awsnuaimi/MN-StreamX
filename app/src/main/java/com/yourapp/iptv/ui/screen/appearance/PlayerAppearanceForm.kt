package com.yourapp.iptv.ui.screen.appearance

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
        AppearanceChoice("حجم النص", value.textSize,
            listOf(PlayerTextSize.SMALL to "صغير", PlayerTextSize.NORMAL to "عادي", PlayerTextSize.LARGE to "كبير"), enabled) {
            onChange(value.copy(textSize = it))
        }
        AppearanceChoice("خلفية المعلومات", value.panelShade,
            listOf(PlayerPanelShade.SOFT to "شفافة", PlayerPanelShade.BALANCED to "متوازنة", PlayerPanelShade.SOLID to "داكنة"), enabled) {
            onChange(value.copy(panelShade = it))
        }
        AppearanceChoice("الإخفاء التلقائي بعد", value.displaySeconds,
            listOf(3 to "3 ثوانٍ", 5 to "5 ثوانٍ", 8 to "8 ثوانٍ"), enabled) {
            onChange(value.copy(displaySeconds = it))
        }
        AppearanceToggle("إظهار شعار القناة", value.showLogo, enabled) { onChange(value.copy(showLogo = it)) }
        AppearanceToggle("إظهار تصنيف القناة", value.showGroup, enabled) { onChange(value.copy(showGroup = it)) }
        AppearanceToggle("إظهار ترتيب القناة وعدد القنوات", value.showCounter, enabled) { onChange(value.copy(showCounter = it)) }
        AppearanceToggle("إظهار البرنامج التالي", value.showNext, enabled) { onChange(value.copy(showNext = it)) }
        AppearanceToggle("إظهار سطر وصف مختصر", value.showDescription,
            enabled) { onChange(value.copy(showDescription = it)) }
        AppearanceToggle("إظهار زري القناة السابقة والتالية", value.showChannelButtons, enabled) {
            onChange(value.copy(showChannelButtons = it))
        }
        AppearanceToggle("إظهار شرح اختصارات الريموت", value.showHints, enabled) { onChange(value.copy(showHints = it)) }
    }
}