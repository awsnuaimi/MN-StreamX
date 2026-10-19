package com.yourapp.iptv.ui.screen.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
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
import com.yourapp.iptv.ui.screen.player.deck.PlayerDeckIcon
import com.yourapp.iptv.ui.screen.player.deck.DeckIcon
import com.yourapp.iptv.ui.theme.NeonBlue

@Composable
internal fun PlayerAppearancePreview(value: PlayerAppearance) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * value.textSize.scale)) {
        Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = value.panelShade.alpha)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("معاينة حجم النص والخلفية", color = NeonBlue, style = MaterialTheme.typography.labelMedium)
            Text("اسم القناة · عنوان البرنامج الحالي", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DeckIcon.GUIDE to "الدليل", DeckIcon.CHANNELS to "القنوات", DeckIcon.FAVORITE to "المفضلة").forEach { (icon, label) ->
                    Surface(Modifier.weight(1f).height(68.dp), color = Color(0xFF1D2C36), contentColor = Color.White) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            PlayerDeckIcon(icon, NeonBlue, 22.dp)
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (value.showNext) Text("التالي: عنوان البرنامج القادم", color = Color(0xFFCCD9E8),
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}