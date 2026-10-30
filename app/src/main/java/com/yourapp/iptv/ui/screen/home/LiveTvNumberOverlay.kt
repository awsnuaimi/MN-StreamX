package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.iptv.ui.presentation.LiveChannelRow

/** Does not take focus or reveal names outside the current allowed, visible selection. */
@Composable
internal fun LiveTvNumberOverlay(entry: LiveTvNumberEntry, match: LiveChannelRow?, labels: HomeLabels,
    colors: HomeTvPalette, modifier: Modifier = Modifier) {
    Surface(modifier.widthIn(max = 430.dp).testTag("live-number-overlay").semantics {
        liveRegion = LiveRegionMode.Polite
    }, shape = RoundedCornerShape(12.dp), color = colors.background,
        border = BorderStroke(1.dp, colors.accent), shadowElevation = 8.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(entry.digits, color = colors.accent, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                style = androidx.compose.material3.LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                modifier = Modifier.testTag("live-number-digits"))
            Text(match?.title ?: labels.text("الرقم غير متاح في الاختيار الحالي", "Number unavailable in the current selection",
                "Nummer in dieser Auswahl nicht verfügbar"), color = colors.text, fontSize = 15.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("live-number-match"))
            Text(labels.text("OK للمشاهدة · رجوع للإلغاء", "OK to watch · Back to cancel", "OK ansehen · Zurück abbrechen"),
                color = colors.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}