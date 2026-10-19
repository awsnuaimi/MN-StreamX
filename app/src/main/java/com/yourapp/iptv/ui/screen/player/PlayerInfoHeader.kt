package com.yourapp.iptv.ui.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.PlayerAppearance
import com.yourapp.iptv.domain.model.PlayerInfoStyle
import com.yourapp.iptv.ui.component.channel.ChannelLogo
import com.yourapp.iptv.ui.component.channel.FavoriteBadge
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.presentation.ProgramPresentation
import com.yourapp.iptv.ui.theme.NeonBlue

@Composable
internal fun PlayerInfoHeader(channel: Channel?, position: Int, total: Int,
    appearance: PlayerAppearance, onBack: () -> Unit, onAppearance: () -> Unit) {
    val compact = appearance.style == PlayerInfoStyle.COMPACT
    Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = appearance.panelShade.alpha))
        .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!compact) AppButton("رجوع للقنوات", onBack)
        if (channel != null && appearance.showLogo) ChannelLogo(channel.name, channel.logoUrl,
            Modifier.size(if (compact) 36.dp else 48.dp))
        Column(Modifier.weight(1f)) {
            Text(channel?.name ?: "MN StreamX", color = Color.White, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = (if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
                    .copy(textDirection = TextDirection.Content))
            if (appearance.showGroup) channel?.group?.let {
                Text(it, color = NeonBlue, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content))
            }
        }
        FavoriteBadge(channel?.isFavorite == true)
        if (appearance.showCounter) Text(ProgramPresentation.playerPosition(position, total), color = Color(0xFFCCD9E8),
            style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Rtl))
        PlayerActionButton("المظهر", onAppearance)
    }
}