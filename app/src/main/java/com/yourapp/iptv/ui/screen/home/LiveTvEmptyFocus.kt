package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun LiveTvEmptyFocus(kind: String, labels: HomeLabels, colors: HomeTvPalette,
    entry: FocusRequester, categories: FocusRequester, sidebar: FocusRequester?, sidebarOnRight: Boolean,
    ready: (Boolean) -> Unit, retry: () -> Unit, clear: () -> Unit, modifier: Modifier = Modifier) {
    val latestReady by rememberUpdatedState(ready)
    DisposableEffect(Unit) { onDispose { latestReady(false) } }
    if (kind == "loading") {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val error = kind == "retry"
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (error) labels.text("تعذّر تحميل تفضيلات القائمة.", "Cannot load list preferences.", "Listeneinstellungen konnten nicht geladen werden.")
            else labels.text("لا توجد قنوات تطابق الاختيار.", "No matching channels.", "Keine passenden Sender."), color = colors.text)
        LiveTvPill(if (error) labels.text("إعادة المحاولة", "Retry", "Erneut versuchen")
            else labels.text("مسح التصفية", "Clear filters", "Filter löschen"), colors, if (error) retry else clear,
            Modifier.focusRequester(entry).homeFocusLinks(up = categories,
                left = sidebar.takeUnless { sidebarOnRight }, right = sidebar.takeIf { sidebarOnRight })
                .onGloballyPositioned { latestReady(true) }.testTag("live-empty-action"))
    }
}