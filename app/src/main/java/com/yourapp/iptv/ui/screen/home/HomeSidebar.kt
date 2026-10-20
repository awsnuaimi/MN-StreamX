package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.HomeAppearance
import com.yourapp.iptv.ui.theme.NeonBlue

@Composable
internal fun HomeSidebar(appearance: HomeAppearance, labels: HomeLabels, selected: HomeSection,
    loading: Boolean, returnFocus: FocusRequester, onSelect: (HomeSection) -> Unit, modifier: Modifier = Modifier, organizationReady: Boolean = true) {
    var hasFocus by remember { mutableStateOf(false) }
    val expanded = appearance.expandedSidebar || hasFocus
    // Avoid remeasuring the entire channel grid on every animation frame.
    val width = if (expanded) 180.dp else 70.dp
    Column(modifier.width(width).fillMaxHeight().background(Color(0xFF0C1428))
        .onFocusChanged { hasFocus = it.hasFocus }.focusGroup().padding(8.dp)
        .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (expanded) "MN StreamX" else "MN", Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
            color = NeonBlue, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        HomeSection.entries.forEach { section ->
            var focused by remember(section) { mutableStateOf(false) }
            val active = selected == section
            Surface(onClick = { onSelect(section) }, enabled = !(loading && section == HomeSection.REFRESH) && (section != HomeSection.ORGANIZE || organizationReady),
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                    .then(if (section == HomeSection.LIVE) Modifier.focusRequester(returnFocus) else Modifier)
                    .onFocusChanged { focused = it.isFocused }.semantics { this.selected = active; contentDescription = labels.section(section) },
                color = when { focused -> Color(0xFF1C4D6D); active -> Color(0xFF16344D); else -> Color.Transparent },
                shape = MaterialTheme.shapes.medium,
                border = if (focused) BorderStroke(2.dp, NeonBlue) else null) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeSidebarIcon(section, if (active || focused) NeonBlue else Color(0xFFB1C3D8))
                    if (expanded) Text(labels.section(section), color = Color.White,
                        style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}