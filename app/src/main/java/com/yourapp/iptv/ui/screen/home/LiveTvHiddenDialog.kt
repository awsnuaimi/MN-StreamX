package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.LiveTvPreferences
import com.yourapp.iptv.ui.component.organization.organizationFolderLabels
import com.yourapp.iptv.ui.presentation.LiveChannelRows
import com.yourapp.iptv.ui.state.HomeUiState

@Composable
internal fun LiveTvHiddenDialog(catalog: HomeUiState, value: LiveTvPreferences, labels: HomeLabels,
    colors: HomeTvPalette, saving: Boolean, change: ((LiveTvPreferences) -> LiveTvPreferences) -> Unit,
    dismiss: () -> Unit) {
    val names = organizationFolderLabels()
    val channels = remember(catalog.channels, value.hiddenChannels) { catalog.channels.filter { it.id in value.hiddenChannels } }
    val groups = remember(catalog.channels, value.hiddenGroups) {
        catalog.channels.mapNotNull { it.group }.distinct().filter { it in value.hiddenGroups }
    }
    val folders = remember(catalog.channels, catalog.organization, value.hiddenFolders) {
        val permitted = catalog.channels.flatMap { it.folderIds }.toSet()
        catalog.organization.folders.filter { it.id in value.hiddenFolders && it.id in permitted }
    }
    val close = remember { FocusRequester() }
    var closeReady by remember { mutableStateOf(false) }
    LaunchedEffect(closeReady, value.hiddenChannels, value.hiddenGroups, value.hiddenFolders) {
        if (closeReady) close.requestFocus()
    }
    AlertDialog(onDismissRequest = dismiss, containerColor = colors.selected,
        title = { Text(labels.text("القنوات والتصنيفات المخفية", "Hidden channels and categories", "Ausgeblendete Sender und Kategorien")) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 330.dp).testTag("live-hidden-list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item("hint") { Text(labels.text("الإخفاء ينظّم هذه القائمة فقط ولا يحل محل الرقابة الأبوية.",
                    "Hiding organizes this list; it does not replace parental controls.",
                    "Ausblenden ordnet diese Liste und ersetzt keine Kindersicherung.")) }
                item("all") { Button(onClick = { change { it.showAll() } }, enabled = !saving,
                    modifier = Modifier.fillMaxWidth().testTag("live-hidden-restore-all")) {
                    Text(labels.text("إظهار كل المخفي", "Show all hidden items", "Alle ausgeblendeten Einträge anzeigen"))
                } }
                items(groups, key = { "group:$it" }) { group ->
                    OutlinedButton(onClick = { change { it.copy(hiddenGroups = it.hiddenGroups - group) } }, enabled = !saving,
                        modifier = Modifier.fillMaxWidth().testTag("live-hidden-group-$group")) {
                        Text(labels.text("إظهار التصنيف: ", "Show category: ", "Kategorie einblenden: ") + LiveChannelRows.category(group))
                    }
                }
                items(folders, key = { "folder:${it.id}" }) { folder ->
                    OutlinedButton(onClick = { change { it.copy(hiddenFolders = it.hiddenFolders - folder.id) } }, enabled = !saving,
                        modifier = Modifier.fillMaxWidth().testTag("live-hidden-folder-${folder.id}")) {
                        Text(labels.text("إظهار التصنيف: ", "Show category: ", "Kategorie einblenden: ") +
                            folder.name.ifBlank { names[folder.label].orEmpty() })
                    }
                }
                items(channels, key = { "channel:${it.id}" }) { channel ->
                    OutlinedButton(onClick = { change { it.copy(hiddenChannels = it.hiddenChannels - channel.id) } }, enabled = !saving,
                        modifier = Modifier.fillMaxWidth().testTag("live-hidden-channel-${channel.id}")) {
                        Text(labels.text("إظهار: ", "Show: ", "Einblenden: ") + channel.name)
                    }
                }
                if (channels.isEmpty() && groups.isEmpty() && folders.isEmpty()) item("empty") {
                    Text(labels.text("لا توجد عناصر مخفية متاحة في هذا المصدر.", "No hidden items available in this source.",
                        "Keine ausgeblendeten Einträge in dieser Quelle verfügbar."))
                }
            }
        }, confirmButton = { TextButton(onClick = dismiss, modifier = Modifier.focusRequester(close)
            .focusProperties { canFocus = true }.onGloballyPositioned { closeReady = true }.testTag("live-hidden-close")) {
            Text(labels.text("إغلاق", "Close", "Schließen"))
        } })
}