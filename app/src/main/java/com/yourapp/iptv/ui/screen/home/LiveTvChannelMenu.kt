package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.ui.presentation.HomeChannelPresentation
import com.yourapp.iptv.ui.presentation.LiveChannelRow

@Composable
internal fun LiveTvChannelMenu(row: LiveChannelRow, channel: Channel, labels: HomeLabels, colors: HomeTvPalette,
    canFavorite: Boolean, onDismiss: () -> Unit, onFavorite: () -> Unit, onPlay: (Channel) -> Unit,
    onTranslate: () -> Unit, onHide: (() -> Unit)? = null, onHideCategory: (() -> Unit)? = null,
    onPreferred: ((Channel?) -> Unit)? = null, preferredQualities: Map<String, String> = emptyMap(), saving: Boolean = false) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = colors.selected,
        title = { Text(row.title, color = colors.text) },
        text = { LazyColumn(Modifier.fillMaxWidth().heightIn(max = 330.dp).testTag("live-menu-variants"),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item("@favorite") {
            Button(onClick = onFavorite, enabled = canFavorite, modifier = Modifier.fillMaxWidth().testTag("live-menu-favorite")) {
                Text(if (channel.isFavorite) labels.text("إزالة من المفضلة", "Remove favorite", "Favorit entfernen")
                    else labels.text("إضافة للمفضلة", "Add favorite", "Als Favorit speichern"))
            }
            }
            items(row.variants, key = { it.id }) { variant ->
                LiveTvPill(labels.text("مشاهدة", "Watch", "Ansehen") +
                    (HomeChannelPresentation.quality(variant)?.let { " · $it" } ?: ""), colors, { onPlay(variant) },
                    Modifier.fillMaxWidth().testTag("live-menu-quality-${variant.id}"))
            }
            if (onPreferred != null) {
                item("@quality-heading") { Text(labels.text("الجودة الافتراضية للقناة", "Default channel quality", "Standardqualität dieses Senders")) }
                item("@quality-auto") {
                    OutlinedButton(onClick = { onPreferred(null) }, enabled = !saving, modifier = Modifier.fillMaxWidth()
                        .testTag("live-prefer-auto")) { Text(labels.text("تلقائي", "Automatic", "Automatisch")) }
                }
                items(row.variants, key = { "prefer:${it.id}" }) { variant ->
                    OutlinedButton(onClick = { onPreferred(variant) }, enabled = !saving, modifier = Modifier.fillMaxWidth()
                        .testTag("live-prefer-${variant.id}")) {
                        Text((if (variant.id == preferredQualities[row.preferenceKey]) "✓ " else "") + (HomeChannelPresentation.quality(variant) ?: variant.name))
                    }
                }
            }
            if (onHide != null) item("@hide") {
                OutlinedButton(onClick = onHide, enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("live-menu-hide")) {
                    Text(labels.text("إخفاء القناة بكل جوداتها", "Hide channel and all qualities", "Sender mit allen Qualitäten ausblenden"))
                }
            }
            if (onHideCategory != null) item("@hide-category") {
                OutlinedButton(onClick = onHideCategory, enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("live-menu-hide-category")) {
                    Text(labels.text("إخفاء التصنيف", "Hide category", "Kategorie ausblenden"))
                }
            }
            item("@translation") {
            LiveTvPill(labels.text("إعدادات الترجمة / مشاهدة مترجمة", "Translation setup / translated playback",
                "Übersetzung einrichten / ansehen"), colors, onTranslate, Modifier.fillMaxWidth())
            }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(labels.text("إغلاق", "Close", "Schließen")) } })
}