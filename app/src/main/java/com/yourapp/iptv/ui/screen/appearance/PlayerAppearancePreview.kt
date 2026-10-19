package com.yourapp.iptv.ui.screen.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.PlayerAppearance
import com.yourapp.iptv.ui.theme.NeonBlue

@Composable
internal fun PlayerAppearancePreview(value: PlayerAppearance) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * value.textSize.scale)) {
        Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = value.panelShade.alpha)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("معاينة حجم النص والخلفية", color = NeonBlue, style = MaterialTheme.typography.labelMedium)
            Text("اسم القناة · عنوان البرنامج الحالي", color = Color.White, style = MaterialTheme.typography.titleMedium)
            if (value.showNext) Text("التالي: عنوان البرنامج القادم", color = Color(0xFFCCD9E8),
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}